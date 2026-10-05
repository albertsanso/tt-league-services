"""Incrementally parse the FCTT match day pages of the current season into JSON actas.

Input pages are the ones saved by ``download_actas_content_incremental.py``:

    <input_dir>/<season>/<category>/<group>/<phase>/jornada-<N>.html
    <input_dir>/<season>/<category>/<group>/<phase>/jornada-<N>/<match_id>.pdf   (ignored for now)

Every ``match-container`` of a page becomes one JSON file following
docs/acta-model-definition.json:

    <output_dir>/<season>/<category>/<group>/<phase>/jornada_<N>_local_team_<home_id>_away_team_<away_id>.json

Path segments are normalized to kebab-case (e.g. "Vet 1a" -> "vet-1a").

The ``acta-content-status`` of each match (``data-acta-content-status`` attribute,
falling back to the page ``<meta name="acta-content-status">``) decides what is
extracted:

* ``complete``: the full acta (line-ups, doubles, games and sets, running score,
  referee, final result) with ``acta_publicada: true``.
* ``partial`` (not played yet) or ``incomplete`` (played, acta not fully
  identified): only the minimum info (season, category, group, phase, match id,
  date, time, teams and venue) with ``acta_publicada: false``.

Runs are incremental: a page whose content has not changed since the previous run
is not parsed again (``--force`` re-parses everything), JSON files are only
rewritten when their content changes, and the JSON files a page produced earlier
but no longer produces (e.g. a rescheduled pairing) are removed. Every record is
validated against the acta model when ``jsonschema`` is installed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from ingest_fctt.download import (
    Node, atomic_write, parse_filter, parse_html, parse_match_days, query_param, slugify,
)

from ingest_common import health
from ingest_common.validation import ACTA_SCHEMA_PATH

try:
    import jsonschema
except ImportError:  # validation is optional
    jsonschema = None


DEFAULT_SCHEMA = ACTA_SCHEMA_PATH
STATE_FILE = ".parse_state.json"
# Bump when the parsing logic changes so that every page is parsed again.
PARSER_VERSION = 1
LOGGER = logging.getLogger("fctt-incremental-parse")

FEDERATION = "Federació Catalana de Tennis Taula"
GENDERS = {"male": "masculino", "female": "femenino"}
STATUSES = ("complete", "partial", "incomplete")
SEASON_RE = re.compile(r"\d{4}-\d{4}")
JORNADA_FILE_RE = re.compile(r"jornada-(\d+)\.html", re.I)
LEAGUE_SEASON_RE = re.compile(r"\s*-?\s*Temporada\s+\d{4}\s*[/-]\s*\d{4}.*$", re.I)
GROUP_NUMBER_RE = re.compile(r"(?:g|grup|grupo|group)-?(\d+)")
SCORE_RE = re.compile(r"(\d+)\s*-\s*(\d+)")
# "... C/ MAGALLANES, 22-24,08291 RIPOLLET": the town follows the postal code (Spanish or Andorran "AD200").
TOWN_RE = re.compile(r"\b(?:\d{5}|AD\d{3})\s*,?\s*([^\d,.][^,.]*?)\s*\.?$", re.I)
# Phase folder slug -> label used in the acta model (``regular`` is the first phase).
PHASE_LABELS = {
    "regular": "1a Fase", "1a-fase": "1a Fase", "2a-fase": "2a Fase", "3a-fase": "3a Fase",
    "titol": "TITOL", "descens": "DESCENS", "ascens": "ASCENS", "fase-final": "Fase Final",
    "play-off": "Play Off", "play-off-titol": "Play Off Títol", "play-off-ascens": "Play Off Ascens",
    "play-off-descens": "Play Off Descens",
}
ABC = {"A", "B", "C"}


# --------------------------------------------------------------------------- input pages


@dataclass
class MatchDayPage:
    """One ``jornada-<N>.html`` and its position in the content tree."""

    path: Path
    season: str
    category: str
    group: str
    phase: str
    jornada: int

    @property
    def relative(self) -> Path:
        return Path(self.season, self.category, self.group, self.phase, self.path.name)

    @property
    def output_folder(self) -> Path:
        return Path(self.season, self.category, self.group, self.phase)

    @property
    def key(self) -> str:
        return self.relative.as_posix()


def discover_pages(input_dir: Path, *, seasons: set[str] | None, categories: set[str] | None,
                   groups: set[str] | None, phases: set[str] | None,
                   match_days: set[int] | None) -> tuple[list[MatchDayPage], int]:
    """Match day pages under ``input_dir`` that pass the filters, and the number of PDF actas found."""
    pages: list[MatchDayPage] = []
    pdfs = 0
    for path in sorted(input_dir.rglob("*")):
        if not path.is_file():
            continue
        parts = path.relative_to(input_dir).parts
        if path.suffix.casefold() == ".pdf":
            pdfs += 1
            continue
        found = JORNADA_FILE_RE.fullmatch(path.name)
        if not found or len(parts) != 5 or not SEASON_RE.fullmatch(parts[0]):
            if path.suffix.casefold() == ".html":
                LOGGER.debug("Ignored (not <season>/<category>/<group>/<phase>/jornada-N.html): %s", path)
            continue
        season, category, group, phase = parts[0], slugify(parts[1]), slugify(parts[2]), slugify(parts[3])
        if not (category and group and phase):
            LOGGER.warning("Ignored, empty category/group/phase folder name: %s", path)
            continue
        jornada = int(found.group(1))
        if ((seasons is not None and season not in seasons)
                or (categories is not None and category not in categories)
                or (groups is not None and group not in groups)
                or (phases is not None and phase not in phases)
                or (match_days is not None and jornada not in match_days)):
            continue
        pages.append(MatchDayPage(path, season, category, group, phase, jornada))
    pages.sort(key=lambda page: (page.season, page.category, page.group, page.phase, page.jornada))
    return pages, pdfs


def read_meta(doc: Node) -> dict[str, str]:
    """``<meta name="acta-...">`` tags written by the downloader."""
    return {node.attrs["name"]: node.attrs.get("content", "") for node in doc.find_all("meta")
            if node.attrs.get("name", "").startswith("acta-")}


def league_title(doc: Node) -> str | None:
    """Competition name from the league heading, without the season ("Tercera Divisió Masculina Grup 1")."""
    node = doc.find(cls="br-apic-league-title")
    title = LEAGUE_SEASON_RE.sub("", node.text()).strip(" -") if node else ""
    return title or None


def fallback_title(doc: Node) -> str | None:
    """Page heading ("TDM G1") or ``<title>`` without the jornada, for pages without league heading."""
    node = doc.find(cls="entry-title")
    if node and node.text():
        return node.text()
    title = doc.find("title")
    text = re.sub(r"\s*[-|–].*$", "", title.text()).strip() if title else ""
    return text or None


# --------------------------------------------------------------------------- values


def clean(value: str | None) -> str:
    return " ".join((value or "").split())


def parse_score(value: str | None) -> tuple[int, int] | None:
    found = SCORE_RE.search(value or "")
    return (int(found.group(1)), int(found.group(2))) if found else None


def score_dict(score: tuple[int, int] | None) -> dict[str, int] | None:
    return {"local": score[0], "visitante": score[1]} if score else None


def labelled(node: Node | None) -> str | None:
    """Text of an info block without its ``<strong>Label:</strong>`` prefix."""
    if node is None:
        return None
    return re.sub(r"^[^:]{1,40}:\s*", "", node.text()).strip() or None


def parse_datetime(text: str) -> tuple[str | None, str | None]:
    date = time_ = None
    if found := re.search(r"(\d{1,2})/(\d{1,2})/(\d{4})", text):
        try:
            date = datetime(int(found.group(3)), int(found.group(2)), int(found.group(1))).date().isoformat()
        except ValueError:
            LOGGER.warning("Invalid match date '%s'", found.group(0))
    if found := re.search(r"\b(\d{1,2}):(\d{2})\b", text):
        if int(found.group(1)) < 24 and int(found.group(2)) < 60:
            time_ = f"{int(found.group(1)):02d}:{found.group(2)}"
    return date, time_


def parse_location(text: str | None) -> dict[str, str | None] | None:
    if not text:
        return None
    town = TOWN_RE.search(text)
    return {"ciudad": town.group(1).strip() if town else None, "recinto": text}


def phase_label(phase: str) -> str:
    return PHASE_LABELS.get(phase) or phase.replace("-", " ").capitalize()


def group_number(group: str) -> int | None:
    """``g1`` -> 1; ``other`` -> None; groups without number (female divisions) -> 0."""
    if group == "other":
        return None
    found = GROUP_NUMBER_RE.fullmatch(group)
    return int(found.group(1)) if found else 0


def team_key(team_id: str | None, name: str) -> str:
    return team_id if team_id and team_id.isdigit() else slugify(name) or "unknown"


# --------------------------------------------------------------------------- match parsing


@dataclass
class ParsedMatch:
    record: dict[str, Any]
    filename: str
    published: bool
    warnings: list[str] = field(default_factory=list)


@dataclass
class Team:
    name: str
    team_id: str | None


def read_team(container: Node, cls: str) -> Team:
    node = container.find("div", cls)
    if node is None:
        return Team("", None)
    link = node.find("a")
    return Team(node.text(), query_param(link.attrs.get("href", ""), "team_name_id") if link else None)


def team_dict(team: Team) -> dict[str, Any]:
    return {"id": team.team_id or None, "nombre": team.name or None, "delegado": None, "entrenador": None}


def players_in(cell: Node | None) -> tuple[list[dict[str, str]], list[str]]:
    """Players of a ``player-info`` cell (two for doubles) and the names without ``codi_jugador`` link."""
    if cell is None:
        return [], []
    players: list[dict[str, str]] = []
    for link in cell.find_all("a"):
        code = query_param(link.attrs.get("href", ""), "codi_jugador")
        name = link.text()
        if name and code and code.isdigit():
            players.append({"nombre": name, "licencia": code, "id": code})
    linked = {player["nombre"] for player in players}
    unlinked = [name for name in cell.lines() if name not in linked]
    # Names without player link keep a placeholder licence ("0") so that the record stays in the model.
    players.extend({"nombre": name, "licencia": "0"} for name in unlinked)
    return players, unlinked


def first_column_is_home(table: Node, home: Team, away: Team, final: tuple[int, int] | None,
                         last_running: tuple[int, int] | None) -> tuple[bool, str | None]:
    """Whether the first player column of the results table belongs to the home team.

    The table header names the team of each column; when it does not match either team, the
    running score is used: the first column belongs to the away team when the last running score
    mirrors the final score.
    """
    head = table.find("thead")
    headers = [clean(cell.text()).casefold() for cell in (head.find_all("th") if head else [])]
    names = [header for header in headers if header]
    if names:
        if names[0] == clean(home.name).casefold():
            return True, None
        if names[0] == clean(away.name).casefold():
            return False, None
    if final and last_running and final[0] != final[1] and last_running == (final[1], final[0]):
        return False, "results table orientation inferred from the running score (away team first)"
    return True, "results table orientation not identified by its header; home team assumed first"


def decided_reason(running: tuple[int, int], total: int) -> str:
    if total and max(running) > total // 2:
        return f"Victoria decidida ({running[0]}-{running[1]})"
    return "Partido no disputado"


def parse_results(container: Node, home: Team, away: Team,
                  final: tuple[int, int] | None) -> tuple[dict[str, Any], list[str]]:
    """Line-ups, doubles and games of a played match (``abc_es_local``, ``alineaciones``, ``dobles``, ``partidos``)."""
    warnings: list[str] = []
    table = container.find("table", "match-results-table")
    rows = [row for row in (table.find_all("tr") if table else []) if row.find("td", "position")]
    if table is None or not rows:
        return {}, []

    raw = []
    for row in rows:
        positions = [clean(cell.text()) for cell in row.find_all("td", "position")]
        cells = row.find_all("td", "player-info")
        raw.append({
            "letters": (positions + ["", ""])[:2],
            "players": [players_in(cell) for cell in (cells + [None, None])[:2]],
            "games": [parse_score(cell.text()) for cell in row.find_all("td", "game")],
            "result": parse_score(row.find("td", "result").text() if row.find("td", "result") else ""),
            "running": parse_score(row.find("td", "global").text() if row.find("td", "global") else ""),
        })
    last_running = next((item["running"] for item in reversed(raw) if item["running"]), None)
    home_first, note = first_column_is_home(table, home, away, final, last_running)
    if note:
        warnings.append(note)

    def oriented(pair: Any) -> Any:
        """(first column, second column) -> (home, away)."""
        if pair is None:
            return None
        return (pair[0], pair[1]) if home_first else (pair[1], pair[0])

    lineups: dict[str, dict[str, Any]] = {"local": {}, "visitante": {}}
    doubles: dict[str, list[dict[str, str]]] | None = None
    games: list[dict[str, Any]] = []
    running = (0, 0)
    for number, item in enumerate(raw, 1):
        letters = oriented(item["letters"])
        (local_players, local_unlinked), (visitor_players, visitor_unlinked) = oriented(item["players"])
        for name in local_unlinked + visitor_unlinked:
            warnings.append(f"game {number}: player without codi_jugador link: {name}")
        is_doubles = any(letter.casefold() in ("db", "d") for letter in letters)
        sides: dict[str, dict[str, Any]] = {}
        for side, letter, players in (("local", letters[0], local_players), ("visitante", letters[1], visitor_players)):
            participant: dict[str, Any] = {"letra": "Db" if is_doubles else letter}
            if is_doubles:
                participant["jugadores"] = players if len(players) == 2 else []
                if len(players) not in (0, 2):
                    warnings.append(f"game {number}: doubles pair with {len(players)} player(s) ({side})")
            elif players:
                participant.update(players[0])
                if letter in lineups[side] and lineups[side][letter]["nombre"] != players[0]["nombre"]:
                    warnings.append(f"game {number}: letter {letter} ({side}) used by two players")
                lineups[side].setdefault(letter, players[0])
            else:
                participant["nombre"] = None
            sides[side] = participant
        if is_doubles and sides["local"]["jugadores"] and sides["visitante"]["jugadores"]:
            doubles = {"local": sides["local"]["jugadores"], "visitante": sides["visitante"]["jugadores"]}

        sets = [{"set": index, "local": score[0], "visitante": score[1]}
                for index, score in enumerate((oriented(game) for game in item["games"] if game), 1)]
        result = oriented(item["result"])
        played = bool(sets) or (result is not None and result != (0, 0))
        if played and result is None:
            result = (sum(s["local"] > s["visitante"] for s in sets), sum(s["visitante"] > s["local"] for s in sets))
        winner = None
        if played and result and result[0] != result[1]:
            winner = "local" if result[0] > result[1] else "visitante"
        previous = running
        if winner:
            running = (running[0] + (winner == "local"), running[1] + (winner == "visitante"))
        # The running score printed by the page is authoritative.
        page_running = oriented(item["running"])
        if page_running and page_running != running:
            warnings.append(f"game {number}: running score {page_running} differs from the games ({running})")
            running = page_running
        game: dict[str, Any] = {
            "numero": number,
            "tipo": "dobles" if is_doubles else "individual",
            "cruce": f"{letters[0] or '?'} vs {letters[1] or '?'}",
            "local": sides["local"],
            "visitante": sides["visitante"],
            "sets": sets if played else [],
            "resultado_juegos": score_dict(result) if played else None,
            "ganador": winner,
            "marcador_acumulado": score_dict(running),
        }
        if not played:
            game["no_disputado"] = True
            game["motivo"] = decided_reason(previous, len(raw))
        games.append(game)

    first_letters = {item["letters"][0] for item in raw}
    abc_first = bool(first_letters & ABC)
    return {
        "abc_es_local": abc_first == home_first,
        "alineaciones": lineups,
        "dobles": doubles,
        "partidos": games,
    }, warnings


def match_status(container: Node, page_status: str | None) -> str:
    """``complete``, ``partial`` or ``incomplete`` for one match container."""
    status = container.attrs.get("data-acta-content-status", "").strip().casefold()
    if status in STATUSES:
        return status
    # Without per-match status, a complete page means every match is complete; otherwise only matches
    # with a results table and an acta link are taken as complete.
    if page_status == "complete":
        return "complete"
    has_acta = container.find("table", "match-results-table") and container.find("a", "acta-link")
    return "complete" if has_acta else "partial"


def parse_match(container: Node, page: MatchDayPage, common: dict[str, Any], page_status: str | None) -> ParsedMatch:
    status = match_status(container, page_status)
    home, away = read_team(container, "team-home"), read_team(container, "team-away")
    if not home.name or not away.name:
        raise ValueError("match without home or away team")
    when = container.find("div", "match-datetime")
    date, time_ = parse_datetime(when.text() if when else "")
    final = parse_score(container.find("div", "match-score").text() if container.find("div", "match-score") else "")
    home_key, away_key = team_key(home.team_id, home.name), team_key(away.team_id, away.name)
    record: dict[str, Any] = {
        "id_partido": "_".join((page.season, page.category, page.group, page.phase,
                                f"{home_key}-{away_key}", str(page.jornada))),
        "acta_publicada": False,
        **common,
        "fecha": date,
        "hora": time_,
        "lugar": parse_location(labelled(container.find("div", "field-info"))),
        "equipos": {"local": team_dict(home), "visitante": team_dict(away)},
        "abc_es_local": None,
        "arbitros": {"principal": None, "asistente": None},
        "alineaciones": {"local": {}, "visitante": {}},
        "dobles": None,
        "partidos": [],
        "resultado_final": {"ganador": None, "marcador_partidos": None, "marcador_juegos": None},
        "acta_protestada": False,
    }
    filename = f"jornada_{page.jornada}_local_team_{home_key}_away_team_{away_key}.json"
    parsed = ParsedMatch(record, filename, published=False)
    if status != "complete":
        # Minimum info only; a played match whose acta is not fully identified keeps its final score.
        if status == "incomplete" and final:
            record["resultado_final"].update(winner_and_score(final, home, away))
        return parsed

    results, warnings = parse_results(container, home, away, final)
    parsed.warnings.extend(warnings)
    if final is None and results.get("partidos"):
        last = results["partidos"][-1]["marcador_acumulado"]
        final = (last["local"], last["visitante"])
    if final:
        record["resultado_final"].update(winner_and_score(final, home, away))
    lineups = results.get("alineaciones", {"local": {}, "visitante": {}})
    # The acta model requires a published acta to have games and exactly three players per team.
    publishable = bool(results.get("partidos")) and all(len(lineups[side]) == 3 for side in ("local", "visitante"))
    if not publishable:
        if results:
            parsed.warnings.append("acta without 3 players per team; kept as not published")
        elif final and min(final) == 0 and max(final) > 0:
            parsed.warnings.append(f"walkover {final[0]}-{final[1]} without acta; kept as not published")
        else:
            parsed.warnings.append("marked complete but without results table; kept as not published")
        return parsed

    referee = labelled(container.find("div", "referee-info"))
    games_won = [game["resultado_juegos"] for game in results["partidos"] if game["resultado_juegos"]]
    record.update(results)
    record["acta_publicada"] = True
    record["arbitros"]["principal"] = {"nombre": referee, "licencia": None} if referee else None
    record["resultado_final"]["marcador_juegos"] = {
        "local": sum(score["local"] for score in games_won),
        "visitante": sum(score["visitante"] for score in games_won),
    }
    parsed.published = True
    return parsed


def winner_and_score(final: tuple[int, int], home: Team, away: Team) -> dict[str, Any]:
    winner = None if final[0] == final[1] else (home.name if final[0] > final[1] else away.name)
    return {"ganador": winner or None, "marcador_partidos": score_dict(final)}


# --------------------------------------------------------------------------- page parsing


@dataclass
class PageResult:
    matches: list[ParsedMatch] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


class CompetitionNames:
    """Competition name of each group folder, also for pages without league heading (calendar pages)."""

    def __init__(self) -> None:
        self._cache: dict[Path, str | None] = {}

    def resolve(self, page: MatchDayPage, doc: Node) -> str | None:
        folder = page.path.parent
        if title := league_title(doc):
            self._cache.setdefault(folder, title)
            return title
        if folder not in self._cache:
            self._cache[folder] = None
            for sibling in sorted(folder.glob("jornada-*.html")):
                if sibling == page.path:
                    continue
                try:
                    text = sibling.read_text(encoding="utf-8", errors="replace")
                except OSError:
                    continue
                if "br-apic-league-title" in text and (title := league_title(parse_html(text))):
                    self._cache[folder] = title
                    break
        return self._cache[folder] or fallback_title(doc)


def parse_page(page: MatchDayPage, text: str, competitions: CompetitionNames) -> PageResult:
    doc = parse_html(text)
    meta = read_meta(doc)
    result = PageResult()
    page_status = meta.get("acta-content-status", "").casefold() or None
    if page_status not in (None, "complete", "partial"):
        LOGGER.warning("%s: unknown acta-content-status '%s'", page.key, page_status)
    for name, expected in (("acta-season", page.season), ("acta-category", page.category),
                           ("acta-group", page.group), ("acta-phase", page.phase),
                           ("acta-match-day", str(page.jornada))):
        if meta.get(name) and slugify(meta[name]) != slugify(expected):
            LOGGER.warning("%s: %s '%s' differs from the folder ('%s'); the folder is used",
                           page.key, name, meta[name], expected)
    gender = meta.get("acta-gender", "").casefold()
    if gender not in GENDERS:
        gender = "female" if "femen" in page.category else "male"
    common = {
        "federacion": FEDERATION,
        "temporada": page.season.replace("-", "/"),
        "genero": GENDERS[gender],
        "competicion": competitions.resolve(page, doc),
        "fase": phase_label(page.phase),
        "grupo": group_number(page.group),
        "jornada": page.jornada,
    }
    containers = doc.find_all("div", "match-container")
    if not containers:
        message = "no matches found"
        if "No s'han trobat resultats" in text:
            message += " (\"No s'han trobat resultats\")"
        result.errors.append(message)
        return result
    expected = meta.get("acta-matches", "")
    if expected.isdigit() and int(expected) != len(containers):
        LOGGER.warning("%s: %s matches expected (acta-matches), %s found", page.key, expected, len(containers))
    for number, container in enumerate(containers, 1):
        try:
            result.matches.append(parse_match(container, page, common, page_status))
        except (ValueError, KeyError, IndexError, TypeError) as error:
            result.errors.append(f"match {number}: {error}")
            health.parse_error()
    seen: dict[str, int] = {}
    for match in result.matches:
        copy = seen[match.filename] = seen.get(match.filename, 0) + 1
        if copy > 1:
            match.warnings.append(f"duplicated pairing in the match day; saved as copy {copy}")
            match.filename = match.filename.replace(".json", f"_{copy}.json")
            match.record["id_partido"] += f"-{copy}"
    return result


# --------------------------------------------------------------------------- incremental run


class SchemaValidator:
    def __init__(self, schema_path: Path | None) -> None:
        self.validator = None
        if schema_path is None:
            return
        if jsonschema is None:
            LOGGER.warning("jsonschema is not installed; JSON records are not validated against %s", schema_path)
            return
        try:
            schema = json.loads(schema_path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            LOGGER.warning("Acta model %s not loaded (%s); JSON records are not validated", schema_path, error)
            return
        cls = jsonschema.validators.validator_for(schema)
        self.validator = cls(schema, format_checker=cls.FORMAT_CHECKER)

    def errors(self, record: dict[str, Any]) -> list[str]:
        if self.validator is None:
            return []
        return [f"{'/'.join(map(str, error.absolute_path)) or '<root>'}: {error.message}"
                for error in sorted(self.validator.iter_errors(record), key=lambda error: list(error.absolute_path))]


@dataclass
class Options:
    input_dir: Path
    output_dir: Path
    seasons: set[str] | None = None
    categories: set[str] | None = None
    groups: set[str] | None = None
    phases: set[str] | None = None
    match_days: set[int] | None = None
    force: bool = False
    dry_run: bool = False
    schema: Path | None = DEFAULT_SCHEMA


class IncrementalParser:
    def __init__(self, options: Options) -> None:
        self.options = options
        self.validator = SchemaValidator(options.schema)
        self.competitions = CompetitionNames()
        self.state_path = options.output_dir / STATE_FILE
        self.state = self._load_state()
        self.counters = dict.fromkeys(
            ("pages", "pages_parsed", "pages_unchanged", "pages_failed", "matches", "published", "not_published",
             "json_written", "json_unchanged", "json_removed", "invalid", "warnings", "pdf_ignored"), 0)

    def _load_state(self) -> dict[str, Any]:
        try:
            state = json.loads(self.state_path.read_text(encoding="utf-8"))
        except FileNotFoundError:
            return {"parser_version": PARSER_VERSION, "pages": {}}
        except (OSError, ValueError) as error:
            LOGGER.warning("Parse state %s not readable (%s); every page is parsed again", self.state_path, error)
            return {"parser_version": PARSER_VERSION, "pages": {}}
        if state.get("parser_version") != PARSER_VERSION or not isinstance(state.get("pages"), dict):
            LOGGER.info("Parser version changed; every page is parsed again")
            return {"parser_version": PARSER_VERSION, "pages": state.get("pages") or {}, "stale": True}
        return state

    def _save_state(self) -> None:
        if self.options.dry_run:
            return
        self.state.pop("stale", None)
        self.state["parser_version"] = PARSER_VERSION
        self.state["updated_at"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        atomic_write(self.state_path, (json.dumps(self.state, ensure_ascii=False, indent=1, sort_keys=True) + "\n").encode("utf-8"))

    def run(self) -> bool:
        options = self.options
        pages, pdfs = discover_pages(options.input_dir, seasons=options.seasons, categories=options.categories,
                                     groups=options.groups, phases=options.phases, match_days=options.match_days)
        self.counters["pdf_ignored"] = pdfs
        if pdfs:
            LOGGER.info("%s PDF acta(s) found and ignored (PDF parsing is not supported yet)", pdfs)
        if not pages:
            LOGGER.warning("No match day pages found in %s for the selected filters", options.input_dir)
            return True
        LOGGER.info("%s match day page(s) to check in %s", len(pages), options.input_dir)
        try:
            for page in pages:
                self.counters["pages"] += 1
                self._process(page)
        finally:
            self._save_state()
        return self.counters["pages_failed"] == 0 and self.counters["invalid"] == 0

    def _process(self, page: MatchDayPage) -> None:
        try:
            data = page.path.read_bytes()
        except OSError as error:
            LOGGER.error("%s: not readable: %s", page.key, error)
            self.counters["pages_failed"] += 1
            return
        digest = hashlib.sha256(data).hexdigest()
        previous = self.state["pages"].get(page.key, {})
        outputs = [self.options.output_dir / name for name in previous.get("outputs", [])]
        if (not self.options.force and not self.state.get("stale") and previous.get("sha256") == digest
                and outputs and all(path.exists() for path in outputs)):
            LOGGER.debug("%s: unchanged since the last run", page.key)
            self.counters["pages_unchanged"] += 1
            return

        try:
            result = parse_page(page, data.decode("utf-8", errors="replace"), self.competitions)
        except Exception as error:  # a broken page must not stop the run
            LOGGER.exception("%s: parsing failed: %s", page.key, error)
            health.parse_error()
            self.counters["pages_failed"] += 1
            return
        self.counters["pages_parsed"] += 1
        for message in result.errors:
            LOGGER.error("%s: %s", page.key, message)
        if not result.matches:
            self.counters["pages_failed"] += 1
            return

        written: list[str] = []
        for match in result.matches:
            relative = (page.output_folder / match.filename).as_posix()
            written.append(relative)
            self.counters["matches"] += 1
            self.counters["published" if match.published else "not_published"] += 1
            for warning in match.warnings:
                LOGGER.warning("%s: %s: %s", page.key, match.filename, warning)
                self.counters["warnings"] += 1
            if errors := self.validator.errors(match.record):
                self.counters["invalid"] += 1
                LOGGER.error("%s: %s does not follow the acta model: %s", page.key, match.filename, "; ".join(errors[:5]))
            self._write(self.options.output_dir / relative, match.record)

        published = sum(match.published for match in result.matches)
        LOGGER.info("%s: %s match(es), %s published, %s not published", page.key, len(result.matches),
                    published, len(result.matches) - published)
        if result.errors:
            # Keep previous outputs and parse the page again next run.
            return
        for stale in sorted(set(previous.get("outputs", [])) - set(written)):
            path = self.options.output_dir / stale
            if path.exists():
                LOGGER.info("%s: removing JSON no longer produced by the page: %s", page.key, path)
                if not self.options.dry_run:
                    path.unlink()
                self.counters["json_removed"] += 1
        self.state["pages"][page.key] = {
            "sha256": digest,
            "status": read_status(data),
            "outputs": written,
            "parsed_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        }

    def _write(self, path: Path, record: dict[str, Any]) -> None:
        content = (json.dumps(record, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        try:
            if path.exists() and path.read_bytes() == content:
                self.counters["json_unchanged"] += 1
                return
        except OSError:
            pass
        if not self.options.dry_run:
            atomic_write(path, content)
        self.counters["json_written"] += 1
        LOGGER.debug("JSON %s: %s", "would be written" if self.options.dry_run else "written", path)


def read_status(data: bytes) -> str | None:
    found = re.search(rb'<meta name="acta-content-status" content="([a-z]+)"', data)
    return found.group(1).decode() if found else None


# --------------------------------------------------------------------------- CLI


def current_season(input_dir: Path) -> str | None:
    seasons = sorted(path.name for path in input_dir.iterdir() if path.is_dir() and SEASON_RE.fullmatch(path.name))
    return seasons[-1] if seasons else None


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--season", help="Season YYYY-YYYY, or 'all' (default: latest season in --input_dir)")
    parser.add_argument("--category", help="Category slug(s), comma separated, e.g. tdm, copa-catalana-femenina (default: all)")
    parser.add_argument("--group", help="Group slug(s), comma separated, e.g. g1, 1a-divisio (default: all)")
    parser.add_argument("--phase", help="Phase slug(s), comma separated, e.g. regular (default: all)")
    parser.add_argument("--match_day", type=parse_match_days, help="Match days, e.g. 3, 1-5 or 1,4,7 (default: all)")
    parser.add_argument("--input_dir", type=Path, required=True)
    parser.add_argument("--output_dir", type=Path, required=True)
    parser.add_argument("--log_file", type=Path, required=True)
    parser.add_argument("--schema", type=Path, default=DEFAULT_SCHEMA, help="Acta model used to validate the JSON files")
    parser.add_argument("--no_validate", action="store_true", help="Do not validate the JSON files against the acta model")
    parser.add_argument("--force", action="store_true", help="Parse every page even if it has not changed")
    parser.add_argument("--dry_run", action="store_true", help="Parse and validate without writing any file")
    parser.add_argument("--verbose", action="store_true", help="Debug output")
    return parser


def configure_logging(log_file: Path, verbose: bool) -> None:
    log_file.parent.mkdir(parents=True, exist_ok=True)
    level = logging.DEBUG if verbose else logging.INFO
    formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    file_handler = logging.FileHandler(log_file, encoding="utf-8")
    console = logging.StreamHandler(sys.stderr)
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(errors="replace")
    for handler in (file_handler, console):
        handler.setLevel(level)
        handler.setFormatter(formatter)
    LOGGER.handlers[:] = [file_handler, console]
    LOGGER.setLevel(level)
    LOGGER.propagate = False


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if args.season and args.season.casefold() != "all" and not SEASON_RE.fullmatch(args.season):
        parser.error("--season must look like 2026-2027 (or 'all')")
    configure_logging(args.log_file, args.verbose)
    if not args.input_dir.is_dir():
        LOGGER.error("Input directory not found: %s", args.input_dir)
        return 2
    if args.season and args.season.casefold() == "all":
        seasons = None
    else:
        season = args.season or current_season(args.input_dir)
        if not season:
            LOGGER.error("No season folder (YYYY-YYYY) in %s", args.input_dir)
            return 2
        seasons = {season}
        LOGGER.info("Season %s", season)

    options = Options(
        input_dir=args.input_dir, output_dir=args.output_dir, seasons=seasons,
        categories=parse_filter(args.category), groups=parse_filter(args.group), phases=parse_filter(args.phase),
        match_days=args.match_day, force=args.force, dry_run=args.dry_run,
        schema=None if args.no_validate else args.schema,
    )
    runner = IncrementalParser(options)
    started = datetime.now()
    ok = False
    try:
        ok = runner.run()
    except KeyboardInterrupt:
        LOGGER.warning("Interrupted; parse state saved")
    counters = runner.counters
    LOGGER.info(
        "Done in %.1f s%s: %s page(s) parsed, %s unchanged, %s failed; %s match(es) (%s published, %s not published); "
        "JSON %s written, %s unchanged, %s removed; %s invalid, %s warning(s), %s PDF ignored",
        (datetime.now() - started).total_seconds(), " (dry run)" if args.dry_run else "",
        counters["pages_parsed"], counters["pages_unchanged"], counters["pages_failed"], counters["matches"],
        counters["published"], counters["not_published"], counters["json_written"], counters["json_unchanged"],
        counters["json_removed"], counters["invalid"], counters["warnings"], counters["pdf_ignored"])
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
