"""Download model "calendar checked" (FCTT): match day pages are checked against the league calendar and saved as served.

For every league the group page (``/lligues/<group>/``) is read once: it holds the season calendar (date, time, teams
and venue of every match), which is used to detect future match days and changes since the previous run. Each match
day page (``?jornada=N``) is then stored with ``<meta name="acta-...">`` tags (``acta-content-status`` is
``complete`` when every match has a validated acta and ``partial`` otherwise) and a ``data-acta-content-status`` /
``data-match-id`` attribute on every ``match-container`` for the incremental parser.

A match day page is accepted only when it lists the matches of the calendar and, for every played match, the results
table, the acta link and a ``codi_jugador`` link for every player; otherwise it is retried with exponential backoff.
A future match day without results is not retried: it is saved with the minimal match info taken from the calendar.
Runs are idempotent: complete match days and future match days that already have a saved page are not requested
again, even if the calendar changed (use ``--force`` to re-download).
"""

from __future__ import annotations

import argparse
import hashlib
import html as html_lib
import json
import logging
import re
import sys
import time
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Callable

from bs4 import BeautifulSoup, Tag

from ingest_common import progress
from ingest_common.region import region_values
from ingest_fcttweb import clock
from ingest_fcttweb.discovery import League, discover
from ingest_fcttweb.files import atomic_write
from ingest_fcttweb.http import ContentRejected, FetchClient, Fetched
from ingest_fcttweb.logging_setup import configure_script_logger
from ingest_fcttweb.markup import (
    Reader, day_sections, has_class, make_soup, query_value, selector_days, slugify,
)
from ingest_fcttweb.metrics import CHRONIC_FAILURES, AttemptLogMetrics, Tuning
from ingest_fcttweb.profile import INDEX_URL, SiteProfile

BLOCK_TITLE_RE = re.compile(r"access denied|attention required|just a moment|forbidden|captcha", re.I)
SEASON_RE = re.compile(r"Temporada\s+(\d{4})\s*[/-]\s*(\d{4})", re.I)
PHASE_RE = re.compile(
    r"(\d+\s*[aª]?\s*fase|fase\s+[\w-]+|play[\s-]*off(?:\s+(?:t[ií]tol|ascens|descens))?|t[ií]tol|descens|ascens)", re.I)


# --------------------------------------------------------------------------- domain


@dataclass
class Player:
    code: str
    name: str


@dataclass
class DayMatch:
    jornada: int | None
    date: str | None  # YYYY-MM-DD
    time: str | None  # HH:MM
    home: str
    away: str
    home_id: str | None
    away_id: str | None
    score: str
    location: str | None
    referee: str | None
    acta_url: str | None
    rows: list[dict[str, Any]] = field(default_factory=list)
    issues: list[str] = field(default_factory=list)

    @property
    def match_id(self) -> str:
        if self.acta_url and (found := re.search(r"/partido/(\d+)", self.acta_url)):
            return found.group(1)
        return f"{self.home_id or slugify(self.home)}-{self.away_id or slugify(self.away)}"

    @property
    def pair(self) -> tuple[str, str]:
        return self.home_id or self.home, self.away_id or self.away

    @property
    def score_values(self) -> tuple[int, int] | None:
        found = re.fullmatch(r"\s*(\d+)\s*-\s*(\d+)\s*", self.score or "")
        return (int(found.group(1)), int(found.group(2))) if found else None

    @property
    def played(self) -> bool:
        values = self.score_values
        return values is not None and (bool(self.rows) or sum(values) > 0)

    @property
    def walkover(self) -> bool:
        """Played without games (e.g. 6 - 0 by no-show): no results table and no acta."""
        values = self.score_values
        return (values is not None and not self.rows and not self.acta_url
                and min(values) == 0 and max(values) > 0)

    @property
    def content_status(self) -> str:
        """``partial`` (not played yet), ``complete`` or ``incomplete`` (played, acta not fully identified)."""
        if not self.played:
            return "partial"
        if self.walkover:
            return "complete"
        return "incomplete" if self.issues or not self.rows or not self.acta_url else "complete"

    def starts_at(self) -> datetime | None:
        if not self.date:
            return None
        year, month, day = (int(part) for part in self.date.split("-"))
        hour, minute = (int(part) for part in (self.time or "00:00").split(":"))
        return datetime(year, month, day, hour, minute, tzinfo=clock.LOCAL_TZ)

    def calendar_summary(self) -> list[Any]:
        return [self.date, self.time, self.home_id, self.away_id, self.home, self.away, self.score, self.location]

    def content_summary(self) -> list[Any]:
        return self.calendar_summary() + [self.referee, self.acta_url, self.rows]


@dataclass
class Target:
    """A league with the season and phase resolved from its page: where its files go."""

    league: League
    season: str
    phase: str
    layout: Any

    @property
    def key(self) -> str:
        return f"{self.season}/{self.league.category}/{self.league.group}"

    def page_url(self, jornada: int) -> str:
        return f"{self.league.url}?jornada={jornada}"

    def group_folder(self, output_dir: Path) -> Path:
        return output_dir / self.season / self.league.category / self.league.group

    def html_path(self, output_dir: Path, jornada: int) -> Path:
        return self.group_folder(output_dir) / self.phase / self.layout.html_name.format(day=jornada)

    def pdf_dir(self, output_dir: Path, jornada: int) -> Path:
        return self.group_folder(output_dir) / self.phase / self.layout.pdf_folder.format(day=jornada)

    def match_day_key(self, jornada: int) -> str:
        return f"{self.key}/{self.phase}/jornada-{jornada}"


def labelled(reader: Reader, node: Tag | None) -> str | None:
    """Text of an info block without its ``<strong>Label:</strong>`` prefix."""
    if node is None:
        return None
    value = re.sub(r"^[^:]{1,40}:\s*", "", reader.text(node))
    return value or None


def _team(reader: Reader, container: Tag, cls: str) -> tuple[str, str | None]:
    node = container.find("div", class_=cls)
    if node is None:
        return "", None
    link = node.find("a")
    return reader.text(node), query_value(str(link.get("href", "")), "team_name_id") if isinstance(link, Tag) else None


def extract_match(reader: Reader, container: Tag, jornada: int | None) -> DayMatch:
    """Extract one ``div.match-container`` (scheduled or played)."""
    when = container.find("div", class_="match-datetime")
    when_text = reader.text(when) if when else ""
    date = time_ = None
    if found := re.search(r"(\d{1,2})/(\d{1,2})/(\d{4})", when_text):
        date = f"{found.group(3)}-{int(found.group(2)):02d}-{int(found.group(1)):02d}"
    if found := re.search(r"(\d{1,2}):(\d{2})", when_text):
        time_ = f"{int(found.group(1)):02d}:{found.group(2)}"
    home, home_id = _team(reader, container, "team-home")
    away, away_id = _team(reader, container, "team-away")
    score_node = container.find("div", class_="match-score")
    acta = container.find("a", class_="acta-link") or next(
        (link for link in container.find_all("a") if "/acta" in str(link.get("href", ""))), None)
    match = DayMatch(
        jornada=jornada, date=date, time=time_, home=home, away=away, home_id=home_id, away_id=away_id,
        score=reader.text(score_node) if score_node else "",
        location=labelled(reader, container.find("div", class_="field-info")),
        referee=labelled(reader, container.find("div", class_="referee-info")),
        acta_url=str(acta.get("href", "")) if acta and "href" in acta.attrs else None,
    )
    table = container.find("table", class_="match-results-table")
    for row_number, row in enumerate(table.find_all("tr") if table else [], 1):
        cells = row.find_all("td")
        if not cells:
            continue
        players: list[list[dict[str, str]]] = []
        for cell in (cell for cell in cells if has_class(cell, "player-info")):
            names = reader.lines(cell)
            links = [link for link in cell.find_all("a") if "codi_jugador" in str(link.get("href", ""))]
            cell_players = []
            for link in links:
                player = Player(query_value(str(link.get("href", "")), "codi_jugador") or "", reader.text(link))
                if not player.code.isdigit():
                    match.issues.append(f"row {row_number}: invalid codi_jugador for {player.name or '?'}")
                if not player.name:
                    match.issues.append(f"row {row_number}: player {player.code} without name")
                cell_players.append({"code": player.code, "name": player.name})
            if len(names) != len(links):
                unlinked = names if not links else [name for name in names if name not in {p["name"] for p in cell_players}]
                match.issues.append(f"row {row_number}: player(s) without codi_jugador link: {', '.join(unlinked) or names}")
            players.append(cell_players)
        match.rows.append({"cells": [reader.text(cell) for cell in cells], "players": players})
    return match


def extract_sections(reader: Reader, soup: BeautifulSoup) -> dict[int | None, list[DayMatch]]:
    """The matches of a page grouped by their ``Jornada N`` heading (None if there is none)."""
    sections, _ = day_sections(soup, reader)
    return {number: [extract_match(reader, container, number) for container in containers]
            for number, containers in sections.items()}


def page_heading_info(reader: Reader, soup: BeautifulSoup) -> tuple[str | None, str | None]:
    """Season and phase named in the league heading, e.g. "Tercera Divisió ... - Temporada 2026/2027"."""
    for heading in soup.find_all("h2"):
        text = reader.text(heading)
        if found := SEASON_RE.search(text):
            phase = PHASE_RE.search(text)
            return f"{found.group(1)}-{found.group(2)}", slugify(phase.group(1)) if phase else None
    return None, None


def is_future(matches: list[DayMatch], now: datetime) -> bool:
    """True when no match of the match day has started yet (unknown dates are not future)."""
    starts = [match.starts_at() for match in matches]
    if clock.LOCAL_TZ is None:
        now = now.replace(tzinfo=None)
    return bool(starts) and all(start is not None and start > now for start in starts)


# --------------------------------------------------------------------------- validation


@dataclass
class Validation:
    status: str  # complete | partial | empty | invalid
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    # True when the page lists the expected matches and only the actas of played matches are incomplete.
    salvageable: bool = False


def validate_matches(matches: list[DayMatch], expected: list[DayMatch]) -> Validation:
    """Check a match day page against the calendar: matches, minimal info and complete actas."""
    if not matches:
        return Validation("empty")
    errors: list[str] = []
    acta_errors: list[str] = []
    warnings: list[str] = []
    if expected:
        if len(matches) != len(expected):
            errors.append(f"expected {len(expected)} matches (calendar), found {len(matches)}")
        elif Counter(m.pair for m in matches) != Counter(m.pair for m in expected):
            errors.append("teams differ from the calendar")
    reference = {m.pair: m for m in expected}
    for number, match in enumerate(matches, 1):
        label = f"match {number} ({match.home or '?'} - {match.away or '?'})"
        ref = reference.get(match.pair)
        if not match.home or not match.away:
            errors.append(f"{label}: missing team")
        for name in ("date", "time", "location"):
            if getattr(match, name):
                continue
            if ref is not None and not getattr(ref, name):
                warnings.append(f"{label}: no {name} published")
            else:
                errors.append(f"{label}: missing {name}")
        if match.walkover:
            warnings.append(f"{label}: walkover {match.score} without acta")
        elif match.played:
            if not match.rows:
                acta_errors.append(f"{label}: played ({match.score}) but without results table")
            if not match.acta_url:
                acta_errors.append(f"{label}: played but without acta link")
            acta_errors.extend(f"{label}: {issue}" for issue in match.issues)
        elif ref is not None and ref.played:
            errors.append(f"{label}: calendar shows result {ref.score} but the page does not")
    if errors or acta_errors:
        return Validation("invalid", errors + acta_errors, warnings, salvageable=not errors)
    return Validation("complete" if all(m.played for m in matches) else "partial", [], warnings)


# --------------------------------------------------------------------------- storage


def fingerprint(value: Any) -> str:
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()[:20]


def read_saved_meta(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    text = path.read_text(encoding="utf-8", errors="replace")
    return {name: html_lib.unescape(value)
            for name, value in re.findall(r'<meta name="(acta-[\w-]+)" content="([^"]*)"', text)}


def render_meta(meta: dict[str, str]) -> str:
    return "".join(f'\n<meta name="{name}" content="{html_lib.escape(value, quote=True)}">' for name, value in meta.items())


def annotate_page(text: str, meta: dict[str, str], matches: list[DayMatch]) -> str:
    """Add the ``acta-*`` meta tags and per-match status attributes to a downloaded page."""
    head = re.search(r"<head\b[^>]*>", text, re.I)
    text = text[:head.end()] + render_meta(meta) + text[head.end():] if head else render_meta(meta) + text
    pattern = re.compile(r'<div class="match-container"')
    if len(pattern.findall(text)) == len(matches):
        statuses = iter(matches)

        def mark(_: re.Match[str]) -> str:
            match = next(statuses)
            return (f'<div class="match-container" data-acta-content-status="{match.content_status}"'
                    f' data-match-id="{html_lib.escape(match.match_id, quote=True)}"')

        text = pattern.sub(mark, text)
    return text


def build_minimal_page(target: Target, jornada: int, matches: list[DayMatch], meta: dict[str, str],
                       source_url: str) -> str:
    """Page with the minimal info of scheduled matches, using the site's own markup."""
    escape = html_lib.escape
    blocks = []
    for match in matches:
        day = "/".join(reversed(match.date.split("-"))) if match.date else ""
        blocks.append(
            f'<div class="match-container" data-acta-content-status="partial" data-match-id="{escape(match.match_id)}">'
            f'<div class="match-datetime">{escape(day)} · {escape(match.time or "")}</div>'
            f'<div class="match-teams-row"><div class="match-teams">'
            f'<div class="team-home"><a href="?team_name_id={escape(match.home_id or "")}">{escape(match.home)}</a></div>'
            f'<div class="match-score">{escape(match.score or "- - -")}</div>'
            f'<div class="team-away"><a href="?team_name_id={escape(match.away_id or "")}">{escape(match.away)}</a></div>'
            f'</div></div><div class="match-info"><div class="field-info"><strong>Terreny de joc:</strong> '
            f'{escape(match.location or "")}</div></div></div>')
    return (f'<!DOCTYPE html>\n<html lang="ca">\n<head>\n<meta charset="utf-8">{render_meta(meta)}\n'
            f'<title>{escape(target.league.title)} - Jornada {jornada}</title>\n</head>\n<body>\n<main>\n'
            f'<h2 class="jornada-results-title"><a href="{escape(source_url)}">Jornada {jornada}</a></h2>\n'
            + "\n".join(blocks) + "\n</main>\n</body>\n</html>\n")


def decode_html(body: bytes, content_type: str = "") -> str:
    charset = re.search(r"charset=([\w-]+)", content_type or "")
    try:
        text = body.decode(charset.group(1) if charset else "utf-8", "replace")
    except LookupError:  # unknown charset name
        text = body.decode("utf-8", "replace")
    if "<html" not in text[:5000].casefold():
        raise ContentRejected("not_html", "response is not an HTML page")
    title = re.search(r"<title[^>]*>(.*?)</title>", text, re.S | re.I)
    if title and BLOCK_TITLE_RE.search(title.group(1)):
        raise ContentRejected("blocked", f"access refused by the server ({title.group(1).strip()[:60]})", retry=False)
    return text


def pdf_validator(body: bytes, content_type: str) -> None:
    if body.startswith(b"%PDF"):
        if b"%%EOF" not in body[-2048:]:
            raise ContentRejected("truncated_pdf", "PDF is truncated")
        return None
    if "html" in content_type or body.lstrip()[:1] == b"<":
        raise ContentRejected("pdf_unavailable", "acta is not available as PDF", retry=False)
    raise ContentRejected("invalid_pdf", "response is not a PDF")


def describe(fetched: Fetched) -> str:
    return f"{fetched.url}: {fetched.message} ({fetched.kind}, {fetched.attempts} attempt(s))"


def _matches(wanted: set[str] | None, *values: str) -> bool:
    return wanted is None or any(slugify(value) in wanted for value in values)


# --------------------------------------------------------------------------- downloader


@dataclass
class Options:
    output_dir: Path = Path("content")
    season: str | None = None
    territories: set[str] | None = None
    categories: set[str] | None = None
    groups: set[str] | None = None
    phases: set[str] | None = None
    match_days: set[int] | None = None
    want_html: bool = True
    want_pdf: bool = False
    force: bool = False
    retries: int = 3


@dataclass
class PageCheck:
    text: str
    matches: list[DayMatch]
    validation: Validation


class IncrementalDownloader:
    def __init__(self, profile: SiteProfile, client: FetchClient, metrics: AttemptLogMetrics, options: Options,
                 tuning: Tuning | None = None, now: Callable[[], datetime] | None = None,
                 logger: logging.Logger | None = None) -> None:
        self.profile = profile
        self.layout = profile.layout
        self.meta = profile.layout.meta
        self.reader = Reader(profile.text_mode)
        self.client = client
        self.metrics = metrics
        self.options = options
        self.tuning = tuning or Tuning()
        self.now = now or clock.madrid_now
        self.logger = logger or logging.getLogger(profile.download_logger)

    def _retries_for(self, key: str, chronic: set[str]) -> int:
        if key in chronic and not self.options.force:
            self.logger.info("%s failed in the last %s runs: only 1 retry", key, CHRONIC_FAILURES)
            return min(1, self.options.retries)
        return self.options.retries

    def _page(self, body: bytes, content_type: str) -> BeautifulSoup:
        return make_soup(decode_html(body, content_type))

    def run(self) -> bool:
        """Download every selected match day; return False if the index could not be read."""
        log = self.logger
        index = self.client.get(INDEX_URL, lambda body, ctype: self._page(body, ctype), retries=self.options.retries)
        if not index.ok:
            log.error("Competitions index not available, skipping the territory: %s", describe(index))
            self.metrics.record_league("index", "failed", describe(index))
            return False
        discovery = discover(self.profile, index.payload, INDEX_URL)
        current, leagues = discovery.season, discovery.leagues
        if not leagues:
            log.error("No TDM / Copa Catalana Femenina leagues found in %s", INDEX_URL)
            self.metrics.record_league("index", "failed", "no leagues found")
            return False
        self.metrics.record_league("index", "ok")
        log.info("Current season %s: %s league(s) discovered", current or "?", len(leagues))
        if self.options.season and current and self.options.season != current:
            log.error("Only the current season (%s) can be downloaded incrementally, not %s",
                      current, self.options.season)
            return False
        selected = [league for league in leagues
                    if _matches(self.options.territories, league.territory)
                    and _matches(self.options.categories, league.category, league.gender or "")
                    and _matches(self.options.groups, league.group)]
        if not selected:
            log.warning("No league matches the filters")
        progress.total(len(selected))
        for league in selected:
            self.process_league(league)
            progress.item(league.title)
        return True

    def _had_content(self, target: Target) -> bool:
        folder = target.group_folder(self.options.output_dir)
        known = self.metrics.data["leagues"].get(target.key, {}).get("jornadas", 0)
        return bool(known) or (folder.exists() and any(folder.rglob(self.layout.html_glob)))

    def process_league(self, league: League) -> None:
        log = self.logger
        key = league.key
        log.info("League %s (%s) -> %s", key, league.title, league.url)
        expect_calendar = self._had_content(Target(league, league.season, self.layout.default_phase, self.layout))

        def validate(body: bytes, content_type: str) -> tuple[BeautifulSoup, dict[int | None, list[DayMatch]], list[int]]:
            soup = self._page(body, content_type)
            sections, selector = extract_sections(self.reader, soup), selector_days(soup)
            if not any(sections.values()) and (selector or expect_calendar):
                raise ContentRejected("no_calendar", "group page lists no matches")
            return soup, sections, selector

        fetched = self.client.get(league.url, validate, retries=self._retries_for(key, self.tuning.chronic_leagues))
        if not fetched.ok:
            log.error("Group page of %s not available, skipping it: %s", key, describe(fetched))
            self.metrics.record_league(key, "failed", describe(fetched))
            return
        soup, sections, selector = fetched.payload
        season, phase = page_heading_info(self.reader, soup)
        if season and season != league.season:
            log.warning("%s page shows season %s (index suggested %s)", key, season, league.season or "?")
            league = league.with_season(season)
        if not league.season:
            log.error("Season of %s unknown (neither the index nor the page names it); skipping", league.title)
            self.metrics.record_league(key, "failed", "unknown season")
            return
        if self.options.season and league.season != self.options.season:
            log.info("Skipping %s: season %s not requested", league.key, league.season)
            return
        target = Target(league, league.season, phase or self.layout.default_phase, self.layout)
        if not _matches(self.options.phases, target.phase):
            return
        calendar = {number: matches for number, matches in sections.items() if number is not None}
        jornadas = sorted(set(calendar) | set(selector))
        if not jornadas:
            log.info("%s has no published calendar yet", target.key)
            self.metrics.record_league(target.key, "not_started")
            return
        self.metrics.record_league(target.key, "ok", jornadas=len(jornadas))
        for jornada in jornadas:
            if self.options.match_days is None or jornada in self.options.match_days:
                self.process_match_day(target, jornada, calendar.get(jornada, []))

    def _match_day_validator(self, jornada: int, expected: list[DayMatch], future: bool):
        def validate(body: bytes, content_type: str) -> PageCheck:
            text = decode_html(body, content_type)
            sections = extract_sections(self.reader, make_soup(text))
            matches = sections.get(jornada)
            if matches is None:
                listed = sorted(number for number in sections if number is not None)
                if listed:
                    raise ContentRejected("wrong_match_day", f"page lists jornada(s) {listed} instead of {jornada}")
                matches = sections.get(None, [])
            validation = validate_matches(matches, expected)
            if validation.status == "empty" and not future:
                raise ContentRejected("no_results", "no results although the match day has already started")
            if validation.status == "invalid":
                raise ContentRejected("incomplete_content", "; ".join(validation.errors[:5]),
                                      PageCheck(text, matches, validation) if validation.salvageable else None)
            return PageCheck(text, matches, validation)

        return validate

    def process_match_day(self, target: Target, jornada: int, calendar: list[DayMatch]) -> None:
        log = self.logger
        key, url = target.match_day_key(jornada), target.page_url(jornada)
        destination = target.html_path(self.options.output_dir, jornada)
        future = is_future(calendar, self.now())
        calendar_fp = fingerprint([match.calendar_summary() for match in calendar])
        saved = read_saved_meta(destination)
        started = time.monotonic()
        status_key, calendar_key = self.meta.status, self.meta.calendar_fingerprint

        def record(outcome: str, status: str | None, attempts: int, errors: list[str] | None = None) -> None:
            self.metrics.record_match_day(key, url=url, outcome=outcome, status=status, attempts=attempts,
                                          duration=time.monotonic() - started, errors=errors or [])

        if not self.options.force and future and destination.is_file() and destination.stat().st_size > 0:
            # Nothing has been played yet and a version is already saved: keep it (use --force to refresh).
            log.debug("Future match day already downloaded: %s (%s)", key, saved.get(status_key))
            record("up_to_date", saved.get(status_key), 0)
            return

        if not self.options.force and saved.get(calendar_key) == calendar_fp and (
                saved.get(status_key) == "complete"
                or (saved.get(status_key) == "partial" and future)):
            log.debug("Up to date: %s (%s)", key, saved.get(status_key))
            if self.options.want_pdf:
                saved_sections = extract_sections(self.reader, make_soup(
                    destination.read_text(encoding="utf-8", errors="replace")))
                self.download_pdfs(target, jornada, saved_sections.get(jornada) or saved_sections.get(None, []))
            record("up_to_date", saved.get(status_key), 0)
            return

        fetched = self.client.get(url, self._match_day_validator(jornada, calendar, future),
                                  retries=self._retries_for(key, self.tuning.chronic_match_days))
        if not fetched.ok:
            if isinstance(fetched.payload, PageCheck):
                # The page lists the expected matches, but some actas stay incomplete after every retry
                # (typically players without codi_jugador link). Keep it as partial so it is retried next run.
                incomplete: PageCheck = fetched.payload
                outcome = self._save(target, jornada, destination, incomplete.matches, "partial", "match_day_page",
                                     calendar_fp, url, saved, incomplete.text)
                log.error("%s still incomplete after %s attempt(s), saved as partial (%s): %s",
                          key, fetched.attempts, outcome, fetched.message)
                if self.options.want_pdf:
                    self.download_pdfs(target, jornada, incomplete.matches)
                record("incomplete", "partial", fetched.attempts, [fetched.message])
                return
            log.error("Skipping %s: %s", key, describe(fetched))
            if future and calendar and self.options.want_html:
                self._save(target, jornada, destination, calendar, "partial", "calendar", calendar_fp, url, saved)
                log.info("Saved minimal match info of future %s from the calendar", key)
            record("failed", None, fetched.attempts, [fetched.message])
            return

        page: PageCheck = fetched.payload
        for warning in page.validation.warnings:
            log.info("%s: %s", key, warning)
        if page.validation.status == "empty":
            if not calendar:
                log.info("%s is a future match day without matches listed anywhere; nothing to save", key)
                record("no_matches", None, fetched.attempts)
                return
            outcome = self._save(target, jornada, destination, calendar, "partial", "calendar", calendar_fp, url, saved)
            log.info("%s not played yet: saved minimal match info from the calendar (%s)", key, outcome)
            record("saved_minimal" if outcome != "unchanged" else outcome, "partial", fetched.attempts)
            return
        status = page.validation.status
        outcome = self._save(target, jornada, destination, page.matches, status, "match_day_page", calendar_fp, url,
                             saved, page.text)
        log.info("%s: %s (%s, %s match(es), %s attempt(s))", key, outcome, status, len(page.matches), fetched.attempts)
        if self.options.want_pdf:
            self.download_pdfs(target, jornada, page.matches)
        record(outcome, status, fetched.attempts)

    def region_meta(self, category: str) -> dict[str, str]:
        values = region_values(self.profile.source, category)
        return dict(zip((self.meta.region, self.meta.region_type), values)) if values else {}

    def _save(self, target: Target, jornada: int, destination: Path, matches: list[DayMatch], status: str,
              origin: str, calendar_fp: str, url: str, saved: dict[str, str], page_text: str | None = None) -> str:
        meta_keys = self.meta
        content_fp = fingerprint([match.content_summary() for match in matches])
        if (not self.options.force and saved.get(meta_keys.content_fingerprint) == content_fp
                and saved.get(meta_keys.status) == status
                and saved.get(meta_keys.calendar_fingerprint) == calendar_fp):
            return "unchanged"
        if not self.options.want_html:
            return "downloaded"
        league = target.league
        meta = {
            meta_keys.status: status, meta_keys.origin: origin, meta_keys.season: target.season,
            meta_keys.territory: league.territory, **self.region_meta(league.category),
            meta_keys.gender: league.gender or "", meta_keys.category: league.category,
            meta_keys.group: league.group, meta_keys.phase: target.phase, meta_keys.match_day: str(jornada),
            meta_keys.matches: str(len(matches)), meta_keys.matches_played: str(sum(m.played for m in matches)),
            meta_keys.matches_incomplete: str(sum(m.content_status == "incomplete" for m in matches)),
            meta_keys.source_url: url, meta_keys.downloaded_at: clock.utc_iso(),
            meta_keys.content_fingerprint: content_fp, meta_keys.calendar_fingerprint: calendar_fp,
        }
        if page_text is None:
            content = build_minimal_page(target, jornada, matches, meta, url)
        else:
            content = annotate_page(page_text, meta, matches)
        atomic_write(destination, content.encode("utf-8"))
        return "updated" if saved else "downloaded"

    def download_pdfs(self, target: Target, jornada: int, matches: list[DayMatch]) -> None:
        folder = target.pdf_dir(self.options.output_dir, jornada)
        for match in matches:
            if not match.played or not match.acta_url:
                continue
            path = folder / f"{match.match_id}.pdf"
            if not self.options.force and path.exists() and path.read_bytes()[:4] == b"%PDF":
                self.metrics.count("pdf_skipped")
                continue
            fetched = self.client.get(match.acta_url, pdf_validator, retries=self.options.retries,
                                      accept=self.profile.http.accept_pdf)
            if not fetched.ok:
                self.logger.error("PDF acta %s of %s not downloaded: %s", match.match_id,
                                  target.match_day_key(jornada), describe(fetched))
                self.metrics.count("pdf_failed")
                continue
            atomic_write(path, fetched.body)
            self.metrics.count("pdf_downloaded")
            self.logger.info("PDF acta saved: %s", path)


# --------------------------------------------------------------------------- CLI


def parse_match_days(value: str) -> set[int]:
    days: set[int] = set()
    for token in filter(None, (part.strip() for part in value.split(","))):
        found = re.fullmatch(r"(\d+)(?:-(\d+))?", token)
        if not found:
            raise argparse.ArgumentTypeError(f"invalid match day '{token}' (use e.g. 3, 1-5 or 1,4,7)")
        start, end = int(found.group(1)), int(found.group(2) or found.group(1))
        if start < 1 or end < start:
            raise argparse.ArgumentTypeError(f"invalid match day range '{token}'")
        days.update(range(start, end + 1))
    return days


def parse_filter(value: str | None) -> set[str] | None:
    if not value or value.strip().casefold() == "all":
        return None
    return {slugify(part) for part in value.split(",") if part.strip()}


def build_parser(profile: SiteProfile, description: str | None = None) -> argparse.ArgumentParser:
    http = profile.http
    parser = argparse.ArgumentParser(description=description or __doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--season", help="Season YYYY-YYYY (default: current season)")
    parser.add_argument("--territory", help="Territory slug(s), comma separated (default: all; currently 'catalunya')")
    parser.add_argument("--category", help="Category slug(s) or gender, comma separated, e.g. tdm, copa-catalana-femenina, female")
    parser.add_argument("--group", help="Group slug(s), comma separated, e.g. g1, 1a-divisio (default: all)")
    parser.add_argument("--phase", help="Phase slug(s), comma separated (default: all)")
    parser.add_argument("--match_day", type=parse_match_days, help="Match days, e.g. 3, 1-5 or 1,4,7 (default: all)")
    parser.add_argument("--format", choices=("html", "pdf", "both"), default="both",
                        help="Content to save (default: both; PDF also requires --no-skip_pdf unless --format pdf)")
    parser.add_argument("--output_dir", type=Path, required=True)
    parser.add_argument("--log_file", type=Path, required=True)
    parser.add_argument("--metrics_file", type=Path, required=True)
    parser.add_argument("--force", action="store_true", help="Re-download content even if it is up to date")
    parser.add_argument("--retry", type=int, default=3, help="Retries for failed downloads (default: 3)")
    parser.add_argument("--skip_pdf", action=argparse.BooleanOptionalAction, default=None,
                        help="Skip PDF actas (default: True, unless --format pdf)")
    parser.add_argument("--request_delay", type=float, default=http.delay,
                        help=f"Minimum seconds between requests (default: {http.delay:g})")
    parser.add_argument("--backoff", type=float, default=http.backoff,
                        help=f"Base seconds of the exponential backoff (default: {http.backoff:g})")
    parser.add_argument("--timeout", type=float, default=http.timeout,
                        help=f"Request timeout in seconds (default: {http.timeout:g})")
    parser.add_argument("--no_adaptive", action="store_true", help="Ignore previous metrics when tuning retries")
    parser.add_argument("--verbose", action="store_true", help="Debug output")
    return parser


def main(profile: SiteProfile, argv: list[str] | None = None) -> int:
    parser = build_parser(profile)
    args = parser.parse_args(argv)
    if args.retry < 0 or args.request_delay < 0 or args.backoff < 0 or args.timeout <= 0:
        parser.error("--retry, --request_delay and --backoff must be >= 0 and --timeout > 0")
    if args.season and not re.fullmatch(r"\d{4}-\d{4}", args.season):
        parser.error("--season must look like 2026-2027")
    skip_pdf = args.skip_pdf if args.skip_pdf is not None else args.format != "pdf"
    logger = configure_script_logger(profile.download_logger, args.log_file,
                                     logging.DEBUG if args.verbose else logging.INFO)

    metrics = AttemptLogMetrics(args.metrics_file, logger)
    tuning = metrics.tuning(adaptive=not args.no_adaptive)
    for note in tuning.notes:
        logger.info("Metrics: %s", note)
    options = Options(
        output_dir=args.output_dir, season=args.season, territories=parse_filter(args.territory),
        categories=parse_filter(args.category), groups=parse_filter(args.group), phases=parse_filter(args.phase),
        match_days=args.match_day, want_html=args.format in ("html", "both"),
        want_pdf=args.format in ("pdf", "both") and not skip_pdf, force=args.force, retries=args.retry,
    )
    client = FetchClient(profile.http, metrics, default_retries=args.retry,
                         delay=args.request_delay * tuning.delay_factor, timeout=args.timeout,
                         backoff=args.backoff * tuning.backoff_factor, logger=logger)
    metrics.start_run({key: (sorted(value) if isinstance(value, set) else str(value) if isinstance(value, Path) else value)
                       for key, value in vars(args).items()}, tuning)
    ok = False
    try:
        ok = IncrementalDownloader(profile, client, metrics, options, tuning, logger=logger).run()
    except KeyboardInterrupt:
        logger.warning("Interrupted; saving metrics")
    finally:
        run = metrics.finish_run()
    counters = run["counters"]
    logger.info("Done in %.1f s: %s downloaded, %s failed, %s skipped, %s retries, %s requests; outcomes %s",
                run["duration_s"], counters["successful_downloads"], counters["failed_downloads"],
                counters["skipped_match_days"], counters["retries"], counters["requests"], run["outcomes"])
    if options.want_pdf:
        logger.info("PDF actas: %s downloaded, %s already present, %s failed",
                    counters["pdf_downloaded"], counters["pdf_skipped"], counters["pdf_failed"])
    if not ok:
        return 2
    return 1 if counters["failed_downloads"] else 0


if __name__ == "__main__":
    from ingest_fcttweb.profile import FCTT_PROFILE

    sys.exit(main(FCTT_PROFILE))
