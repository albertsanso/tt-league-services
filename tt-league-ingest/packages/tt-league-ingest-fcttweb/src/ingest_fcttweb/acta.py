"""Acta assembly: one ``.match-container`` -> one acta following ``docs/acta-model-definition.json``.

Two assemblers exist because the original ports differ in more than naming (``SiteProfile.status_model``):

* ``TableActaBuilder`` ("table-present", BCNESA): a match is published as soon as its results table has rows. The
  table is read by cell classes with a fallback on the cell order and the left column is oriented by the header
  names or the running score.
* ``StatusActaBuilder`` ("match-status", FCTT): the status of the match (``complete`` / ``partial`` /
  ``incomplete``, a walkover is complete) decides what is read; a played match is published only with games and
  three players per team, unlinked players keep a placeholder licence and the running score is recomputed.

Both emit ``region`` / ``tipo_region`` after ``federacion`` and keep the key order of their legacy output.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Callable

from bs4 import Tag

from ingest_common.region import region_for
from ingest_fcttweb.markup import (
    Reader, compact, parse_score, query_value, score_dict, slugify, squash,
)
from ingest_fcttweb.profile import FEDERATION, SiteProfile

LETTERS = {"A", "B", "C", "X", "Y", "Z"}
ABC = {"A", "B", "C"}
DOUBLES_LETTER = "Db"

# --------------------------------------------------------------------------- table-present model (BCNESA)

SELECTORS = {
    "match": ".match-container",
    "datetime": ".match-datetime",
    "home": ".team-home",
    "away": ".team-away",
    "score": ".match-score",
    "table": ".match-results-table",
    "field": ".field-info",
    "referee": ".referee-info",
    "position": "position",
    "player": "player-info",
    "game": "game",
    "result": "result",
    "global": "global",
}


@dataclass(frozen=True)
class ActaLocation:
    """Where an acta sits in the ``season/category/group/phase/file`` tree."""

    season: str
    category: str
    group: str
    phase: str
    stem: str


@dataclass
class GameRow:
    """One row of the results table, as read from the page (left column first)."""

    left_letter: str
    right_letter: str
    left_players: list[dict[str, str]]
    right_players: list[dict[str, str]]
    sets: list[tuple[int, int]] = field(default_factory=list)
    result: tuple[int, int] | None = None
    accumulated: tuple[int, int] | None = None

    @property
    def is_doubles(self) -> bool:
        return DOUBLES_LETTER.casefold() in {self.left_letter.casefold(), self.right_letter.casefold()}


def strip_label(value: str) -> str:
    """Drop a leading ``Label:`` such as ``Terreny de joc:`` or ``Àrbitre:``."""
    return re.sub(r"^[^:]{1,40}:\s*", "", squash(value)).strip()


def category_name(folder: str) -> str:
    """Undo the downloader's file-name cleaning: ``Segona _A_`` -> ``Segona "A"``."""
    return re.sub(r"_([A-Z])_$", r'"\1"', folder).strip()


def group_number_from_folder(folder: str) -> int | None:
    match = re.fullmatch(r"G\s*(\d+)", folder.strip(), re.I)
    return int(match.group(1)) if match else None


def normalise_name(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.casefold())


def swap_score(score: dict[str, Any] | None) -> dict[str, Any] | None:
    return {"local": score["visitante"], "visitante": score["local"]} if score else score


def swap_sides(game: dict[str, Any]) -> dict[str, Any]:
    swapped = dict(game)
    swapped["local"], swapped["visitante"] = game["visitante"], game["local"]
    swapped["sets"] = [{"set": item["set"], **swap_score(item)} for item in game["sets"]]
    swapped["resultado_juegos"] = swap_score(game["resultado_juegos"])
    swapped["marcador_acumulado"] = swap_score(game["marcador_acumulado"])
    if game["ganador"]:
        swapped["ganador"] = "visitante" if game["ganador"] == "local" else "local"
    return swapped


def parse_date_time(value: str) -> tuple[str | None, str | None]:
    """Parse ``04/10/2026 · 11:00`` or ``27/09/2025<br>17:00`` into ISO date and HH:MM."""
    date_value = hour_value = None
    date_match = re.search(r"(\d{1,2})/(\d{1,2})/(\d{2,4})", value)
    if date_match:
        day, month, year = date_match.groups()
        year_format = "%Y" if len(year) == 4 else "%y"
        try:
            date_value = datetime.strptime(f"{day}/{month}/{year}", f"%d/%m/{year_format}").date().isoformat()
        except ValueError:
            pass
    time_match = re.search(r"\b(\d{1,2}):(\d{2})\b", value)
    if time_match and int(time_match.group(1)) < 24 and int(time_match.group(2)) < 60:
        hour_value = f"{int(time_match.group(1)):02d}:{time_match.group(2)}"
    return date_value, hour_value


class TableActaBuilder:
    """BCNESA: a match with a results table is published, any other one only gets its header."""

    def __init__(self, profile: SiteProfile):
        self.profile = profile
        self.reader = Reader(profile.text_mode)

    def build(self, container: Tag, location: ActaLocation, category: str, day: int) -> dict[str, Any]:
        """One ``.match-container`` -> one acta. A match without a results table only gets the minimal fields."""
        header = self.with_region(self.match_header(location, container, day), category)
        final = parse_score(self.reader.text(container.select_one(SELECTORS["score"])))
        table = container.select_one(SELECTORS["table"])
        rows = self.parse_rows(table) if table else []
        if table is None or not rows:
            return self.unpublished_acta(header, final)
        return self.published_acta(header, final, table, rows)

    def stem(self, container: Tag, position: int, day: int) -> str:
        """``acta_{home id}-{away id}_{jornada}``, the stem ``match_id`` turns into ``id_partido``."""
        ids = []
        for selector in (".team-home a[href]", ".team-away a[href]"):
            anchor = container.select_one(selector)
            ids.append(query_value(str(anchor["href"]), "team_name_id") if anchor else None)
        pair = "-".join(str(value) for value in ids) if all(ids) else f"m{position}"
        return f"acta_{pair}_{day}"

    def with_region(self, header: dict[str, Any], category: str) -> dict[str, Any]:
        """``header`` with ``region`` / ``tipo_region`` right after ``federacion``; unknown prefixes raise ValueError."""
        region, region_type = region_for(self.profile.source, category)
        result: dict[str, Any] = {}
        for key, value in header.items():
            result[key] = value
            if key == "federacion":
                result["region"], result["tipo_region"] = region.value, region_type.value
        return result

    def match_id(self, location: ActaLocation) -> str:
        """Unique match id, e.g. ``2026-2027_RTBVETERANS1a_G1_1aFase_151-247_2`` for ``acta_151-247_2``."""
        if self.profile.fixture_id != "compact":
            raise ValueError(f"fixture id format {self.profile.fixture_id!r} is not supported by the table-present model")
        stem = re.sub(r"^acta_", "", location.stem)
        return "_".join([location.season, compact(location.category), compact(location.group),
                         compact(location.phase), stem])

    def match_header(self, location: ActaLocation, container: Tag, day: int) -> dict[str, Any]:
        """Fields known before the match is played: where, when and who (published or not)."""
        reader = self.reader
        date_value, hour_value = parse_date_time(reader.text(container.select_one(SELECTORS["datetime"])))
        return {
            "id_partido": self.match_id(location),
            "federacion": FEDERATION,
            "temporada": location.season.replace("-", "/"),
            "competicion": category_name(location.category),
            "fase": location.phase,
            "grupo": group_number_from_folder(location.group),
            "jornada": day,
            "fecha": date_value,
            "hora": hour_value,
            "lugar": self.parse_venue(container),
            "equipos": {
                "local": self.parse_team(container.select_one(SELECTORS["home"])),
                "visitante": self.parse_team(container.select_one(SELECTORS["away"])),
            },
            "arbitros": {"principal": self.parse_referee(container), "asistente": None},
        }

    def parse_venue(self, container: Tag) -> dict[str, str | None] | None:
        """Split ``PASSEIG ... 198-200, 08860 CASTELLDEFELS`` into venue and town (after the postcode)."""
        venue = strip_label(self.reader.text(container.select_one(SELECTORS["field"])))
        if not venue:
            return None
        town = re.search(r"\b\d{5}\s+([^,]+?)\s*$", venue)
        return {"ciudad": town.group(1).strip() if town else None, "recinto": venue}

    def parse_referee(self, container: Tag) -> dict[str, str | None] | None:
        name = strip_label(self.reader.text(container.select_one(SELECTORS["referee"])))
        return {"nombre": name, "licencia": None} if name else None

    def parse_team(self, cell: Tag | None) -> dict[str, Any]:
        anchor = cell.find("a", href=True) if cell else None
        return {
            "id": query_value(str(anchor["href"]), "team_name_id") if anchor else None,
            "nombre": self.reader.text(cell) or None,
            "delegado": None,
            "entrenador": None,
        }

    def parse_players(self, cell: Tag | None) -> list[dict[str, str]]:
        """The players linked in a ``.player-info`` cell (two for doubles); ``codi_jugador`` is the licence too."""
        if cell is None:
            return []
        players = []
        for anchor in cell.find_all("a"):
            name = self.reader.text(anchor)
            code = query_value(str(anchor.get("href", "")), "codi_jugador")
            if not name:
                continue
            player = {"nombre": name, "licencia": code if code and code.isdigit() else "0"}
            if code and code.isdigit():
                player["id"] = code
            players.append(player)
        if not players and self.reader.text(cell):
            # Unlinked doubles players are separated by <br> (older pages used " / ").
            names = re.split(r"\s*(?:\n|/)\s*", cell.get_text("\n", strip=True))
            players = [{"nombre": squash(name), "licencia": "0"} for name in names if squash(name)]
        return players

    def parse_row(self, row: Tag) -> GameRow | None:
        """Read one results row by cell classes, falling back to the cell order.

        The expected order is: letter, player(s), letter, player(s), set scores..., result, global.
        Header, spacer and malformed rows return None.
        """
        reader = self.reader
        cells = row.find_all("td", recursive=False)

        def by_class(name: str) -> list[Tag]:
            return [cell for cell in cells if name in (cell.get("class") or [])]

        positions, players = by_class(SELECTORS["position"]), by_class(SELECTORS["player"])
        if len(positions) >= 2 and len(players) >= 2:
            games = [reader.text(cell) for cell in by_class(SELECTORS["game"])]
            result = reader.text(by_class(SELECTORS["result"])[0]) if by_class(SELECTORS["result"]) else ""
            accumulated = reader.text(by_class(SELECTORS["global"])[0]) if by_class(SELECTORS["global"]) else ""
        elif len(cells) >= 6:
            positions, players = [cells[0], cells[2]], [cells[1], cells[3]]
            games = [reader.text(cell) for cell in cells[4:-2]]
            result, accumulated = reader.text(cells[-2]), reader.text(cells[-1])
        else:
            return None
        left_letter, right_letter = reader.text(positions[0]), reader.text(positions[1])
        if not left_letter and not right_letter:
            return None
        sets = [score for score in (parse_score(game) for game in games) if score]
        return GameRow(left_letter, right_letter, self.parse_players(players[0]), self.parse_players(players[1]),
                       sets, parse_score(result), parse_score(accumulated))

    def parse_rows(self, table: Tag) -> list[GameRow]:
        body = table.find("tbody") or table
        return [game for game in (self.parse_row(row) for row in body.find_all("tr")) if game]

    def header_team_names(self, table: Tag) -> tuple[str, str]:
        """Team names written above the left and right player columns, if present."""
        header = table.find("thead")
        cells = [self.reader.text(cell) for cell in header.find_all("th")] if header else []
        return (cells[1], cells[3]) if len(cells) >= 4 else ("", "")

    def left_is_home(self, table: Tag, rows: list[GameRow], home: str, away: str,
                     final: tuple[int, int] | None) -> bool:
        """Tell whether the left (usually A/B/C) column of the table is the home team.

        The column headers name the teams; if they are missing, the last running score is compared with the final
        score, which is always written home - away.
        """
        left_name, right_name = (normalise_name(name) for name in self.header_team_names(table))
        home_name, away_name = normalise_name(home), normalise_name(away)
        if left_name and home_name and away_name and home_name != away_name:
            if left_name == away_name or right_name == home_name:
                return False
            if left_name == home_name or right_name == away_name:
                return True
        last = next((row.accumulated for row in reversed(rows) if row.accumulated), None)
        if final and last and final[0] != final[1] and last == (final[1], final[0]):
            return False
        return True

    @staticmethod
    def participant(letter: str, players: list[dict[str, str]], doubles: bool) -> dict[str, Any]:
        if doubles:
            return {"letra": DOUBLES_LETTER, "jugadores": players}
        return {"letra": letter, **players[0]} if players else {"letra": letter, "nombre": None}

    def build_game(self, number: int, row: GameRow, previous: dict[str, int]) -> dict[str, Any]:
        """Build a ``partido`` with the left column as local; ``swap_sides`` flips it if needed."""
        doubles = row.is_doubles
        accumulated = score_dict(row.accumulated) or dict(previous)
        game: dict[str, Any] = {
            "numero": number,
            "tipo": "dobles" if doubles else "individual",
            "cruce": f"{row.left_letter} vs {row.right_letter}",
            "local": self.participant(row.left_letter, row.left_players, doubles),
            "visitante": self.participant(row.right_letter, row.right_players, doubles),
            "sets": [{"set": index, "local": left, "visitante": right} for index, (left, right) in enumerate(row.sets, 1)],
            "resultado_juegos": score_dict(row.result),
            "ganador": None,
            "marcador_acumulado": accumulated,
        }
        if row.result and row.result[0] != row.result[1]:
            game["ganador"] = "local" if row.result[0] > row.result[1] else "visitante"
        if not row.result or row.result == (0, 0) and not row.sets:
            game.update(sets=[], resultado_juegos=None, ganador=None, no_disputado=True, motivo="Resultado no disponible")
        return game

    @staticmethod
    def lineups(rows: list[GameRow]) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any] | None]:
        """Collect the left/right line-ups by letter and the doubles pairs (left first)."""
        left: dict[str, Any] = {}
        right: dict[str, Any] = {}
        doubles = None
        for row in rows:
            if row.is_doubles:
                doubles = {"local": row.left_players, "visitante": row.right_players}
                continue
            if row.left_letter in LETTERS and row.left_players:
                left.setdefault(row.left_letter, row.left_players[0])
            if row.right_letter in LETTERS and row.right_players:
                right.setdefault(row.right_letter, row.right_players[0])
        return left, right, doubles

    @staticmethod
    def final_result(header: dict[str, Any], final: tuple[int, int] | None, games: list[dict[str, Any]]) -> dict[str, Any]:
        """``resultado_final`` from the header score (home - away) and the parsed games."""
        teams = header["equipos"]
        winner = None
        if final and final[0] != final[1]:
            winner = teams["local"]["nombre"] if final[0] > final[1] else teams["visitante"]["nombre"]
        played = [game["resultado_juegos"] for game in games if game["resultado_juegos"]]
        return {
            "ganador": winner,
            "marcador_partidos": score_dict(final),
            "marcador_juegos": {
                "local": sum(score["local"] for score in played),
                "visitante": sum(score["visitante"] for score in played),
            } if played else None,
        }

    def unpublished_acta(self, header: dict[str, Any], final: tuple[int, int] | None) -> dict[str, Any]:
        """An acta for a match that is not played yet or whose results are not published.

        Only the header is known, so line-ups and games are empty. The header score is kept in case the page shows a
        result without the game-by-game table (for example a walkover).
        """
        return {
            "acta_publicada": False,
            **header,
            "abc_es_local": None,
            "alineaciones": {"local": {}, "visitante": {}},
            "dobles": None,
            "partidos": [],
            "resultado_final": self.final_result(header, final, []),
            "acta_protestada": False,
        }

    def published_acta(self, header: dict[str, Any], final: tuple[int, int] | None, table: Tag,
                       rows: list[GameRow]) -> dict[str, Any]:
        """An acta with its results table: line-ups, games and running scores."""
        teams = header["equipos"]
        home_is_left = self.left_is_home(table, rows, teams["local"]["nombre"] or "", teams["visitante"]["nombre"] or "", final)
        left, right, doubles = self.lineups(rows)
        games: list[dict[str, Any]] = []
        previous = {"local": 0, "visitante": 0}
        for number, row in enumerate(rows, 1):
            game = self.build_game(number, row, previous)
            previous = game["marcador_acumulado"]
            games.append(game)
        if not home_is_left:
            left, right = right, left
            doubles = {"local": doubles["visitante"], "visitante": doubles["local"]} if doubles else None
            games = [swap_sides(game) for game in games]
        if final is None:
            last = games[-1]["marcador_acumulado"]
            final = (last["local"], last["visitante"])
        return {
            "acta_publicada": True,
            **header,
            # The left column holds A/B/C, so ABC is local exactly when the left column is local.
            "abc_es_local": home_is_left == (rows[0].left_letter.upper() in {"A", "B", "C"}),
            "alineaciones": {"local": left, "visitante": right},
            "dobles": doubles,
            "partidos": games,
            "resultado_final": self.final_result(header, final, games),
            "acta_protestada": False,
        }


# --------------------------------------------------------------------------- match-status model (FCTT)

GENDERS = {"male": "masculino", "female": "femenino"}
STATUSES = ("complete", "partial", "incomplete")
GROUP_NUMBER_RE = re.compile(r"(?:g|grup|grupo|group)-?(\d+)")
# "... C/ MAGALLANES, 22-24,08291 RIPOLLET": the town follows the postal code (Spanish or Andorran "AD200").
TOWN_RE = re.compile(r"\b(?:\d{5}|AD\d{3})\s*,?\s*([^\d,.][^,.]*?)\s*\.?$", re.I)
# Phase folder slug -> label used in the acta model (``regular`` is the first phase).
PHASE_LABELS = {
    "regular": "1a Fase", "1a-fase": "1a Fase", "2a-fase": "2a Fase", "3a-fase": "3a Fase",
    "titol": "TITOL", "descens": "DESCENS", "ascens": "ASCENS", "fase-final": "Fase Final",
    "play-off": "Play Off", "play-off-titol": "Play Off Títol", "play-off-ascens": "Play Off Ascens",
    "play-off-descens": "Play Off Descens",
}


@dataclass
class PageContext:
    """What the assembler needs to know about the page a match comes from."""

    season: str
    category: str
    group: str
    phase: str
    jornada: int
    competition: str | None
    gender: str  # male | female


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


def phase_label(phase: str) -> str:
    return PHASE_LABELS.get(phase) or phase.replace("-", " ").capitalize()


def group_number_from_slug(group: str) -> int | None:
    """``g1`` -> 1; ``other`` -> None; groups without number (female divisions) -> 0."""
    if group == "other":
        return None
    found = GROUP_NUMBER_RE.fullmatch(group)
    return int(found.group(1)) if found else 0


def team_key(team_id: str | None, name: str) -> str:
    return team_id if team_id and team_id.isdigit() else slugify(name) or "unknown"


def decided_reason(running: tuple[int, int], total: int) -> str:
    if total and max(running) > total // 2:
        return f"Victoria decidida ({running[0]}-{running[1]})"
    return "Partido no disputado"


def winner_and_score(final: tuple[int, int], home: Team, away: Team) -> dict[str, Any]:
    winner = None if final[0] == final[1] else (home.name if final[0] > final[1] else away.name)
    return {"ganador": winner or None, "marcador_partidos": score_dict(final)}


class StatusActaBuilder:
    """FCTT: the status of each match decides whether its acta is read."""

    def __init__(self, profile: SiteProfile, warn: Callable[[str], None] | None = None):
        self.profile = profile
        self.reader = Reader(profile.text_mode)
        self.warn = warn or (lambda message: None)

    # ---- values

    def labelled(self, node: Tag | None) -> str | None:
        """Text of an info block without its ``<strong>Label:</strong>`` prefix."""
        if node is None:
            return None
        return re.sub(r"^[^:]{1,40}:\s*", "", self.reader.text(node)).strip() or None

    def parse_datetime(self, text: str) -> tuple[str | None, str | None]:
        date = time_ = None
        if found := re.search(r"(\d{1,2})/(\d{1,2})/(\d{4})", text):
            try:
                date = datetime(int(found.group(3)), int(found.group(2)), int(found.group(1))).date().isoformat()
            except ValueError:
                self.warn(f"Invalid match date '{found.group(0)}'")
        if found := re.search(r"\b(\d{1,2}):(\d{2})\b", text):
            if int(found.group(1)) < 24 and int(found.group(2)) < 60:
                time_ = f"{int(found.group(1)):02d}:{found.group(2)}"
        return date, time_

    @staticmethod
    def parse_location(text: str | None) -> dict[str, str | None] | None:
        if not text:
            return None
        town = TOWN_RE.search(text)
        return {"ciudad": town.group(1).strip() if town else None, "recinto": text}

    def read_team(self, container: Tag, cls: str) -> Team:
        node = container.find("div", class_=cls)
        if node is None:
            return Team("", None)
        link = node.find("a")
        return Team(self.reader.text(node),
                    query_value(str(link.get("href", "")), "team_name_id") if isinstance(link, Tag) else None)

    @staticmethod
    def team_dict(team: Team) -> dict[str, Any]:
        return {"id": team.team_id or None, "nombre": team.name or None, "delegado": None, "entrenador": None}

    def players_in(self, cell: Tag | None) -> tuple[list[dict[str, str]], list[str]]:
        """Players of a ``player-info`` cell (two for doubles) and the names without ``codi_jugador`` link."""
        if cell is None:
            return [], []
        players: list[dict[str, str]] = []
        for link in cell.find_all("a"):
            code = query_value(str(link.get("href", "")), "codi_jugador")
            name = self.reader.text(link)
            if name and code and code.isdigit():
                players.append({"nombre": name, "licencia": code, "id": code})
        linked = {player["nombre"] for player in players}
        unlinked = [name for name in self.reader.lines(cell) if name not in linked]
        # Names without player link keep a placeholder licence ("0") so that the record stays in the model.
        players.extend({"nombre": name, "licencia": "0"} for name in unlinked)
        return players, unlinked

    def first_column_is_home(self, table: Tag, home: Team, away: Team, final: tuple[int, int] | None,
                             last_running: tuple[int, int] | None) -> tuple[bool, str | None]:
        """Whether the first player column of the results table belongs to the home team.

        The table header names the team of each column; when it does not match either team, the running score is
        used: the first column belongs to the away team when the last running score mirrors the final score.
        """
        head = table.find("thead")
        headers = [squash(self.reader.text(cell)).casefold() for cell in (head.find_all("th") if head else [])]
        names = [header for header in headers if header]
        if names:
            if names[0] == squash(home.name).casefold():
                return True, None
            if names[0] == squash(away.name).casefold():
                return False, None
        if final and last_running and final[0] != final[1] and last_running == (final[1], final[0]):
            return False, "results table orientation inferred from the running score (away team first)"
        return True, "results table orientation not identified by its header; home team assumed first"

    def parse_results(self, container: Tag, home: Team, away: Team,
                      final: tuple[int, int] | None) -> tuple[dict[str, Any], list[str]]:
        """Line-ups, doubles and games of a played match."""
        reader = self.reader
        warnings: list[str] = []
        table = container.find("table", class_="match-results-table")
        rows = [row for row in (table.find_all("tr") if table else []) if row.find("td", class_="position")]
        if table is None or not rows:
            return {}, []

        raw = []
        for row in rows:
            positions = [squash(reader.text(cell)) for cell in row.find_all("td", class_="position")]
            cells = row.find_all("td", class_="player-info")
            result_cell, global_cell = row.find("td", class_="result"), row.find("td", class_="global")
            raw.append({
                "letters": (positions + ["", ""])[:2],
                "players": [self.players_in(cell) for cell in (cells + [None, None])[:2]],
                "games": [parse_score(reader.text(cell)) for cell in row.find_all("td", class_="game")],
                "result": parse_score(reader.text(result_cell) if result_cell else ""),
                "running": parse_score(reader.text(global_cell) if global_cell else ""),
            })
        last_running = next((item["running"] for item in reversed(raw) if item["running"]), None)
        home_first, note = self.first_column_is_home(table, home, away, final, last_running)
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

    def match_status(self, container: Tag, page_status: str | None) -> str:
        """``complete``, ``partial`` or ``incomplete`` for one match container."""
        status = str(container.get("data-acta-content-status", "")).strip().casefold()
        if status in STATUSES:
            return status
        # Without per-match status, a complete page means every match is complete; otherwise only matches
        # with a results table and an acta link are taken as complete.
        if page_status == "complete":
            return "complete"
        has_acta = container.find("table", class_="match-results-table") and container.find("a", class_="acta-link")
        return "complete" if has_acta else "partial"

    def fixture_id(self, page: PageContext, home_key: str, away_key: str) -> str:
        if self.profile.fixture_id == "slug":
            return "_".join((page.season, page.category, page.group, page.phase,
                             f"{home_key}-{away_key}", str(page.jornada)))
        raise ValueError(f"fixture id format {self.profile.fixture_id!r} is not supported by the match-status model")

    def common(self, page: PageContext) -> dict[str, Any]:
        region, region_type = region_for(self.profile.source)
        common: dict[str, Any] = {
            "federacion": FEDERATION,
            "region": region.value,
            "tipo_region": region_type.value,
            "temporada": page.season.replace("-", "/"),
        }
        if self.profile.emits_gender:
            common["genero"] = GENDERS[page.gender]
        common.update({
            "competicion": page.competition,
            "fase": phase_label(page.phase),
            "grupo": group_number_from_slug(page.group),
            "jornada": page.jornada,
        })
        return common

    def parse_match(self, container: Tag, page: PageContext, common: dict[str, Any],
                    page_status: str | None) -> ParsedMatch:
        reader = self.reader
        status = self.match_status(container, page_status)
        home, away = self.read_team(container, "team-home"), self.read_team(container, "team-away")
        if not home.name or not away.name:
            raise ValueError("match without home or away team")
        when = container.find("div", class_="match-datetime")
        date, time_ = self.parse_datetime(reader.text(when) if when else "")
        score_node = container.find("div", class_="match-score")
        final = parse_score(reader.text(score_node) if score_node else "")
        home_key, away_key = team_key(home.team_id, home.name), team_key(away.team_id, away.name)
        record: dict[str, Any] = {
            "id_partido": self.fixture_id(page, home_key, away_key),
            "acta_publicada": False,
            **common,
            "fecha": date,
            "hora": time_,
            "lugar": self.parse_location(self.labelled(container.find("div", class_="field-info"))),
            "equipos": {"local": self.team_dict(home), "visitante": self.team_dict(away)},
            "abc_es_local": None,
            "arbitros": {"principal": None, "asistente": None},
            "alineaciones": {"local": {}, "visitante": {}},
            "dobles": None,
            "partidos": [],
            "resultado_final": {"ganador": None, "marcador_partidos": None, "marcador_juegos": None},
            "acta_protestada": False,
        }
        filename = self.profile.layout.json_name.format(day=page.jornada, home=home_key, away=away_key)
        parsed = ParsedMatch(record, filename, published=False)
        if status != "complete":
            # Minimum info only; a played match whose acta is not fully identified keeps its final score.
            if status == "incomplete" and final:
                record["resultado_final"].update(winner_and_score(final, home, away))
            return parsed

        results, warnings = self.parse_results(container, home, away, final)
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

        referee = self.labelled(container.find("div", class_="referee-info"))
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

