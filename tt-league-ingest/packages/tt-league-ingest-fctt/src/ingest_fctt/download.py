"""Incrementally download FCTT match reports (actas) for the current season.

Leagues are discovered from https://fctt.cat/competicions-estatals/: the TDM
groups (male) and the Copa Catalana Femenina divisions (female) of the current
season. The "Altres temporades" blocks (previous seasons) are ignored.

For every league the group page (``/lligues/<group>/``) is read once: it holds the
season calendar (date, time, teams and venue of every match), which is used to
detect future match days and changes since the previous run. Each match day page
(``?jornada=N``) is then stored as:

    <output_dir>/<season>/<category>/<group>/<phase>/jornada-<N>.html
    <output_dir>/<season>/<category>/<group>/<phase>/jornada-<N>/<match_id>.pdf

Saved HTML files carry ``<meta name="acta-...">`` tags; ``acta-content-status`` is
``complete`` when every match has a validated acta and ``partial`` otherwise.
Every ``match-container`` also gets ``data-acta-content-status`` and
``data-match-id`` attributes for the incremental parser.

A match day page is accepted only when it lists the matches of the calendar and,
for every played match, the results table, the acta link and a ``codi_jugador``
link for every player. Otherwise it is retried with exponential backoff. A future
match day without results is not retried: it is saved with the minimal match
info taken from the calendar.

Runs are idempotent: complete match days and future match days that already have a
saved page are not requested again, even if the calendar changed (use ``--force`` to
re-download).
Request and match day metrics are persisted as JSON and used by later runs to
tune pacing, backoff and the retry budget of match days that keep failing.
"""

from __future__ import annotations

import argparse
import hashlib
import html as html_lib
import http.client
import json
import logging
import os
import random
import re
import sys
import time
import unicodedata
import uuid
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from html.parser import HTMLParser
from http.cookiejar import CookieJar
from pathlib import Path
from typing import Any, Callable, Iterator
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urljoin, urlparse
from urllib.request import HTTPCookieProcessor, Request, build_opener
from urllib.robotparser import RobotFileParser

from ingest_common import health

try:
    from zoneinfo import ZoneInfo

    LOCAL_TZ = ZoneInfo("Europe/Madrid")
except Exception:  # tzdata missing: fall back to naive local time
    LOCAL_TZ = None


BASE_URL = "https://fctt.cat"
INDEX_URL = f"{BASE_URL}/competicions-estatals/"
DEFAULT_PHASE = "regular"
LOGGER = logging.getLogger("fctt-incremental-download")

# Index section heading -> (territory, gender, category slug, label prefix removed from group names).
SECTIONS = {
    "tdm": ("catalunya", "male", "tdm", "tdm"),
    "copa catalana femenina": ("catalunya", "female", "copa-catalana-femenina", ""),
}
OTHER_SEASONS_HEADING = "altres temporades"
HEADING_TAGS = {"h1", "h2", "h3", "h4", "h5", "h6"}

# The first agent identifies the tool (the one already used by src/actas-html/download_actas.py);
# the next ones are used on retries.
USER_AGENTS = (
    "fctt-actas-downloader/1.0 (+https://fctt.cat/lligues/)",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15",
    "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0",
)
# fctt.cat intermittently answers 404 for valid league pages, so 404 is retried too.
# 401/403 are never retried: an explicit refusal is respected, not worked around.
RETRIABLE_STATUS = {404, 408, 425, 429, 500, 502, 503, 504, 520, 521, 522, 523, 524}
THROTTLE_KINDS = ("http_429", "http_503")
BLOCK_TITLE_RE = re.compile(r"access denied|attention required|just a moment|forbidden|captcha", re.I)
SEASON_RE = re.compile(r"Temporada\s+(\d{4})\s*[/-]\s*(\d{4})", re.I)
PHASE_RE = re.compile(
    r"(\d+\s*[aª]?\s*fase|fase\s+[\w-]+|play[\s-]*off(?:\s+(?:t[ií]tol|ascens|descens))?|t[ií]tol|descens|ascens)", re.I)
HTML_ACCEPT = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8"
PDF_ACCEPT = "application/pdf,*/*;q=0.5"
HISTORY_LIMIT = 20
RUNS_LIMIT = 100
CHRONIC_FAILURES = 3


# --------------------------------------------------------------------------- HTML tree


VOID_TAGS = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}
RAW_TEXT_TAGS = {"script", "style", "noscript", "template"}
LINE_BREAK = "\x00"


class Node:
    """Minimal DOM element built with the standard library HTML parser."""

    __slots__ = ("tag", "attrs", "children")

    def __init__(self, tag: str, attrs: dict[str, str] | None = None) -> None:
        self.tag = tag
        self.attrs = attrs or {}
        self.children: list[Node | str] = []

    def has_class(self, name: str) -> bool:
        return name in self.attrs.get("class", "").split()

    def iter(self) -> Iterator[Node]:
        """Yield this element and its descendants in document order."""
        stack: list[Node] = [self]
        while stack:
            node = stack.pop()
            yield node
            stack.extend(reversed([child for child in node.children if isinstance(child, Node)]))

    def find_all(self, tag: str | None = None, cls: str | None = None) -> list[Node]:
        return [node for node in self.iter() if node is not self
                and (tag is None or node.tag == tag) and (cls is None or node.has_class(cls))]

    def find(self, tag: str | None = None, cls: str | None = None) -> Node | None:
        return next((node for node in self.iter() if node is not self
                     and (tag is None or node.tag == tag) and (cls is None or node.has_class(cls))), None)

    def _raw_text(self) -> str:
        parts: list[str] = []
        stack: list[Node | str] = [self]
        while stack:
            item = stack.pop()
            if isinstance(item, str):
                parts.append(item)
            elif item.tag == "br":
                parts.append(LINE_BREAK)
            elif item.tag not in RAW_TEXT_TAGS:
                stack.extend(reversed(item.children))
        return "".join(parts)

    def text(self) -> str:
        return " ".join(self._raw_text().replace(LINE_BREAK, " ").split())

    def lines(self) -> list[str]:
        """Text split on ``<br>``, e.g. the two players of a doubles cell."""
        return [line for line in (" ".join(part.split()) for part in self._raw_text().split(LINE_BREAK)) if line]

    def own_text(self) -> str:
        return " ".join("".join(child for child in self.children if isinstance(child, str)).split())


class _TreeBuilder(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.root = Node("#document")
        self.stack = [self.root]

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        node = Node(tag, {})
        for name, value in attrs:
            node.attrs.setdefault(name, value or "")
        self.stack[-1].children.append(node)
        if tag not in VOID_TAGS:
            self.stack.append(node)

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self.stack[-1].children.append(Node(tag, {name: value or "" for name, value in attrs}))

    def handle_endtag(self, tag: str) -> None:
        # Close the nearest open element with this tag; stray end tags are ignored.
        for index in range(len(self.stack) - 1, 0, -1):
            if self.stack[index].tag == tag:
                del self.stack[index:]
                return

    def handle_data(self, data: str) -> None:
        self.stack[-1].children.append(data)


def parse_html(text: str) -> Node:
    builder = _TreeBuilder()
    builder.feed(text)
    builder.close()
    return builder.root


# --------------------------------------------------------------------------- domain


def slugify(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_text = "".join(char for char in normalized if not unicodedata.combining(char))
    return re.sub(r"[^a-z0-9]+", "-", ascii_text.lower()).strip("-")


def next_season(season: str) -> str:
    start, end = (int(part) for part in season.split("-"))
    return f"{start + 1}-{end + 1}"


def query_param(href: str, name: str) -> str | None:
    return parse_qs(urlparse(href).query).get(name, [None])[0]


def now_local() -> datetime:
    return datetime.now(LOCAL_TZ) if LOCAL_TZ else datetime.now()


@dataclass
class League:
    season: str
    territory: str
    gender: str
    category: str
    group: str
    url: str
    label: str
    phase: str = DEFAULT_PHASE

    @property
    def key(self) -> str:
        return f"{self.season}/{self.category}/{self.group}"

    def page_url(self, jornada: int) -> str:
        return f"{self.url}?jornada={jornada}"

    def group_folder(self, output_dir: Path) -> Path:
        return output_dir / self.season / self.category / self.group

    def html_path(self, output_dir: Path, jornada: int) -> Path:
        return self.group_folder(output_dir) / self.phase / f"jornada-{jornada}.html"

    def pdf_dir(self, output_dir: Path, jornada: int) -> Path:
        return self.group_folder(output_dir) / self.phase / f"jornada-{jornada}"

    def match_day_key(self, jornada: int) -> str:
        return f"{self.key}/{self.phase}/jornada-{jornada}"


@dataclass
class Player:
    code: str
    name: str


@dataclass
class Match:
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
        return datetime(year, month, day, hour, minute, tzinfo=LOCAL_TZ)

    def calendar_summary(self) -> list[Any]:
        return [self.date, self.time, self.home_id, self.away_id, self.home, self.away, self.score, self.location]

    def content_summary(self) -> list[Any]:
        return self.calendar_summary() + [self.referee, self.acta_url, self.rows]


def _labelled(node: Node | None) -> str | None:
    """Text of an info block without its ``<strong>Label:</strong>`` prefix."""
    if node is None:
        return None
    value = re.sub(r"^[^:]{1,40}:\s*", "", node.text())
    return value or None


def _team(container: Node, cls: str) -> tuple[str, str | None]:
    node = container.find("div", cls)
    if node is None:
        return "", None
    link = node.find("a")
    return node.text(), query_param(link.attrs.get("href", ""), "team_name_id") if link else None


def extract_match(container: Node, jornada: int | None) -> Match:
    """Extract one ``div.match-container`` (scheduled or played)."""
    when = container.find("div", "match-datetime")
    when_text = when.text() if when else ""
    date = time_ = None
    if found := re.search(r"(\d{1,2})/(\d{1,2})/(\d{4})", when_text):
        date = f"{found.group(3)}-{int(found.group(2)):02d}-{int(found.group(1)):02d}"
    if found := re.search(r"(\d{1,2}):(\d{2})", when_text):
        time_ = f"{int(found.group(1)):02d}:{found.group(2)}"
    home, home_id = _team(container, "team-home")
    away, away_id = _team(container, "team-away")
    score_node = container.find("div", "match-score")
    acta = container.find("a", "acta-link") or next(
        (link for link in container.find_all("a") if "/acta" in link.attrs.get("href", "")), None)
    match = Match(
        jornada=jornada, date=date, time=time_, home=home, away=away, home_id=home_id, away_id=away_id,
        score=score_node.text() if score_node else "",
        location=_labelled(container.find("div", "field-info")),
        referee=_labelled(container.find("div", "referee-info")),
        acta_url=acta.attrs.get("href") if acta else None,
    )
    table = container.find("table", "match-results-table")
    for row_number, row in enumerate(table.find_all("tr") if table else [], 1):
        cells = row.find_all("td")
        if not cells:
            continue
        players: list[list[dict[str, str]]] = []
        for cell in (cell for cell in cells if cell.has_class("player-info")):
            names = cell.lines()
            links = [link for link in cell.find_all("a") if "codi_jugador" in link.attrs.get("href", "")]
            cell_players = []
            for link in links:
                player = Player(query_param(link.attrs["href"], "codi_jugador") or "", link.text())
                if not player.code.isdigit():
                    match.issues.append(f"row {row_number}: invalid codi_jugador for {player.name or '?'}")
                if not player.name:
                    match.issues.append(f"row {row_number}: player {player.code} without name")
                cell_players.append({"code": player.code, "name": player.name})
            if len(names) != len(links):
                unlinked = names if not links else [name for name in names if name not in {p["name"] for p in cell_players}]
                match.issues.append(f"row {row_number}: player(s) without codi_jugador link: {', '.join(unlinked) or names}")
            players.append(cell_players)
        match.rows.append({"cells": [cell.text() for cell in cells], "players": players})
    return match


def _jornada_number(heading: Node) -> int | None:
    if found := re.search(r"Jornada\s+(\d+)", heading.text(), re.I):
        return int(found.group(1))
    link = heading.find("a")
    value = query_param(link.attrs.get("href", ""), "jornada") if link else None
    return int(value) if value and value.isdigit() else None


def extract_sections(doc: Node) -> dict[int | None, list[Match]]:
    """Group the match containers of a page by their ``Jornada N`` heading (None if there is none)."""
    sections: dict[int | None, list[Match]] = {}
    current: int | None = None
    for node in doc.iter():
        if node.tag in HEADING_TAGS and node.has_class("jornada-results-title"):
            current = _jornada_number(node)
            sections.setdefault(current, [])
        elif node.tag == "div" and node.has_class("match-container"):
            sections.setdefault(current, []).append(extract_match(node, current))
    return sections


def jornada_selector(doc: Node) -> list[int]:
    return sorted({int(node.attrs["data-jornada"]) for node in doc.iter()
                   if node.attrs.get("data-jornada", "").isdigit()})


def page_heading_info(doc: Node) -> tuple[str | None, str | None]:
    """Season and phase named in the league heading, e.g. "Tercera Divisió ... - Temporada 2026/2027"."""
    for heading in doc.find_all("h2"):
        text = heading.text()
        if found := SEASON_RE.search(text):
            phase = PHASE_RE.search(text)
            return f"{found.group(1)}-{found.group(2)}", slugify(phase.group(1)) if phase else None
    return None, None


def discover_leagues(doc: Node) -> tuple[str | None, list[League]]:
    """Return the current season and its leagues from the competitions index page."""
    section: tuple[str, str, str, str] | None = None
    other_seasons = False
    label: str | None = None
    listed_seasons: set[str] = set()
    leagues: list[League] = []
    for node in doc.iter():
        if node.tag in HEADING_TAGS:
            text = node.text()
            key = text.casefold()
            if section and other_seasons and (found := SEASON_RE.search(text)):
                listed_seasons.add(f"{found.group(1)}-{found.group(2)}")
            elif key in SECTIONS:
                section, other_seasons, label = SECTIONS[key], False, None
            elif key == OTHER_SEASONS_HEADING:
                other_seasons, label = True, None
            elif node.tag in ("h1", "h2"):
                section, label = None, None
            elif section and text:
                label = text
            continue
        if section and other_seasons and (found := SEASON_RE.search(node.own_text())):
            listed_seasons.add(f"{found.group(1)}-{found.group(2)}")
        if node.tag != "a" or not section or other_seasons or not label:
            continue
        url = urljoin(INDEX_URL, node.attrs.get("href", "")).split("?")[0].split("#")[0]
        if not urlparse(url).path.startswith("/lligues/"):
            continue
        territory, gender, category, prefix = section
        name = label[len(prefix):].strip() if prefix and label.casefold().startswith(prefix) else label
        leagues.append(League("", territory, gender, category, slugify(name) or slugify(label),
                              url if url.endswith("/") else url + "/", label))
        label = None
    current = next_season(max(listed_seasons)) if listed_seasons else None
    for league in leagues:
        league.season = current or ""
    return current, leagues


def is_future(matches: list[Match], now: datetime) -> bool:
    """True when no match of the match day has started yet (unknown dates are not future)."""
    starts = [match.starts_at() for match in matches]
    if LOCAL_TZ is None:
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


def validate_matches(matches: list[Match], expected: list[Match]) -> Validation:
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


# --------------------------------------------------------------------------- HTTP


class RetryableContent(Exception):
    """The response arrived but its content is not acceptable yet; retry."""

    def __init__(self, kind: str, message: str, payload: Any = None) -> None:
        super().__init__(message)
        self.kind = kind
        self.payload = payload  # best content seen, kept if every retry fails


class RejectedContent(RetryableContent):
    """The response is definitive and not acceptable; do not retry."""


class FetchError(Exception):
    def __init__(self, url: str, kind: str, attempts: int, message: str, status: int | None = None,
                 payload: Any = None) -> None:
        super().__init__(f"{url}: {message} ({kind}, {attempts} attempt(s))")
        self.url, self.kind, self.attempts, self.message, self.status = url, kind, attempts, message, status
        self.payload = payload


@dataclass
class FetchResult:
    url: str
    body: bytes
    status: int
    content_type: str
    attempts: int
    elapsed: float
    payload: Any = None


class Pacer:
    """Enforce a minimum interval between requests."""

    def __init__(self, delay: float, sleep: Callable[[float], None] = time.sleep) -> None:
        self.delay = max(0.0, delay)
        self.sleep = sleep
        self.next_at = 0.0

    def wait(self) -> None:
        remaining = self.next_at - time.monotonic()
        if remaining > 0:
            self.sleep(remaining)
        self.next_at = time.monotonic() + self.delay


def retry_after_seconds(value: str | None) -> float | None:
    if not value:
        return None
    try:
        return max(0.0, float(value))
    except ValueError:
        try:
            return max(0.0, parsedate_to_datetime(value).timestamp() - time.time())
        except (TypeError, ValueError):
            return None


class HttpClient:
    """Polite HTTP client: robots.txt, pacing, exponential backoff, rotating agents, metrics."""

    def __init__(self, metrics: "MetricsStore", *, request_delay: float = 3.0, timeout: float = 30.0,
                 backoff_base: float = 5.0, backoff_max: float = 120.0,
                 sleep: Callable[[float], None] = time.sleep, opener=None) -> None:
        self.metrics = metrics
        self.timeout = timeout
        self.backoff_base = backoff_base
        self.backoff_max = backoff_max
        self.sleep = sleep
        self.pacer = Pacer(request_delay, sleep)
        self.opener = opener or build_opener(HTTPCookieProcessor(CookieJar()))
        self.robots: dict[str, RobotFileParser | None] = {}

    def _open(self, url: str, user_agent: str, accept: str) -> tuple[int, str, bytes]:
        request = Request(url, headers={"User-Agent": user_agent, "Accept": accept,
                                        "Accept-Language": "ca,es;q=0.9,en;q=0.8"})
        with self.opener.open(request, timeout=self.timeout) as response:
            return response.status, response.headers.get("Content-Type", ""), response.read()

    def _robots_for(self, url: str) -> RobotFileParser | None:
        """robots.txt rules of the URL's origin (RFC 9309); None means everything is allowed."""
        parts = urlparse(url)
        origin = f"{parts.scheme}://{parts.netloc}"
        if origin in self.robots:
            return self.robots[origin]
        cache = self.metrics.data.setdefault("robots", {})
        parser: RobotFileParser | None = RobotFileParser(f"{origin}/robots.txt")
        text: str | None = None
        for attempt in range(1, 4):
            try:
                self.pacer.wait()
                _, _, body = self._open(f"{origin}/robots.txt", USER_AGENTS[0], "text/plain")
                text = body.decode("utf-8", "replace")
                cache[origin] = {"fetched_at": utc_now_iso(), "body": text}
                break
            except HTTPError as error:
                if 400 <= error.code < 500 and error.code != 429:
                    LOGGER.info("robots.txt of %s answers HTTP %s: no restrictions", origin, error.code)
                    parser = None  # "unavailable": crawling is allowed
                    break
                problem = f"HTTP {error.code}"
            except (URLError, OSError, http.client.HTTPException) as error:
                problem = str(getattr(error, "reason", error))
            if attempt < 3:
                self.sleep(self._backoff(attempt, None))
        else:
            if origin in cache:
                LOGGER.warning("robots.txt of %s unreachable (%s); using the copy from %s",
                               origin, problem, cache[origin]["fetched_at"])
                text = cache[origin]["body"]
            else:
                LOGGER.error("robots.txt of %s unreachable (%s) and never seen: assuming full disallow", origin, problem)
                parser.disallow_all = True
        if parser is not None and text is not None:
            parser.parse(text.splitlines())
        if parser is not None and (delay := parser.crawl_delay("*")) and float(delay) > self.pacer.delay:
            LOGGER.info("robots.txt of %s asks for a crawl delay of %s s", origin, delay)
            self.pacer.delay = float(delay)
        self.robots[origin] = parser
        return parser

    def _backoff(self, attempt: int, retry_after: str | None) -> float:
        if (server_delay := retry_after_seconds(retry_after)) is not None:
            return min(300.0, server_delay)
        delay = self.backoff_base * 2 ** (attempt - 1) + random.uniform(0, self.backoff_base)
        return min(self.backoff_max, delay)

    def get(self, url: str, *, retries: int, accept: str = HTML_ACCEPT,
            validator: Callable[[bytes, str], Any] | None = None) -> FetchResult:
        """GET ``url`` with up to ``retries`` extra attempts; ``validator`` may reject the content."""
        robots = self._robots_for(url)
        if robots is not None and not robots.can_fetch("*", url):
            raise FetchError(url, "robots_disallowed", 0, "disallowed by robots.txt")
        started = time.monotonic()
        kind, message, status, payload = "unknown", "", None, None
        attempt = 0
        for attempt in range(1, retries + 2):
            self.pacer.wait()
            user_agent = USER_AGENTS[(attempt - 1) % len(USER_AGENTS)]
            request_started = time.monotonic()
            retry_after = None
            retriable = True
            status, payload = None, None
            try:
                status, content_type, body = self._open(url, user_agent, accept)
                payload = validator(body, content_type) if validator else None
            except HTTPError as error:
                status, kind, message = error.code, f"http_{error.code}", f"HTTP {error.code} {error.reason}"
                retriable = error.code in RETRIABLE_STATUS
                retry_after = error.headers.get("Retry-After") if error.headers else None
            except RetryableContent as error:
                kind, message, payload = error.kind, str(error), error.payload
                retriable = not isinstance(error, RejectedContent)
            except TimeoutError as error:
                kind, message = "timeout", str(error) or "timed out"
            except URLError as error:
                timed_out = isinstance(error.reason, TimeoutError)
                kind, message = ("timeout" if timed_out else "network"), str(error.reason)
            except (http.client.HTTPException, OSError) as error:
                kind, message = "network", f"{type(error).__name__}: {error}"
            else:
                elapsed = time.monotonic() - request_started
                self.metrics.record_request(url, attempt, True, None, status, elapsed, len(body))
                return FetchResult(url, body, status, content_type, attempt, time.monotonic() - started, payload)
            self.metrics.record_request(url, attempt, False, kind, status, time.monotonic() - request_started, 0)
            if not retriable or attempt > retries:
                break
            delay = self._backoff(attempt, retry_after)
            LOGGER.warning("Attempt %s/%s failed for %s: %s; retrying in %.1f s", attempt, retries + 1, url, message, delay)
            self.sleep(delay)
        if retriable:
            self.metrics.record_exhausted()
        health.timeout() if kind == "timeout" else health.http_error()
        raise FetchError(url, kind, attempt, message, status, payload)


# --------------------------------------------------------------------------- metrics


@dataclass
class Tuning:
    """Adjustments learned from the metrics of previous runs."""

    delay_factor: float = 1.0
    backoff_factor: float = 1.0
    chronic_match_days: set[str] = field(default_factory=set)
    chronic_leagues: set[str] = field(default_factory=set)
    notes: list[str] = field(default_factory=list)

    def as_dict(self) -> dict[str, Any]:
        return {"delay_factor": round(self.delay_factor, 3), "backoff_factor": round(self.backoff_factor, 3),
                "chronic_match_days": sorted(self.chronic_match_days),
                "chronic_leagues": sorted(self.chronic_leagues), "notes": self.notes}


def _empty_metrics() -> dict[str, Any]:
    return {
        "schema_version": 1,
        "updated_at": None,
        "totals": {"runs": 0, "requests": 0, "failed_requests": 0, "retries": 0, "successful_downloads": 0,
                   "failed_downloads": 0, "skipped_match_days": 0},
        "requests": {
            "latency_seconds": {"count": 0, "mean": 0.0, "m2": 0.0, "min": None, "max": None},
            "attempts_until_success": {},
            "exhausted": 0,
            "errors_by_kind": {},
            "errors_by_status": {},
            "errors_by_hour_utc": {},
            "by_host": {},
        },
        "leagues": {},
        "match_days": {},
        "runs": [],
        "robots": {},
    }


def _bump(counter: dict[str, int], key: Any, amount: int = 1) -> None:
    counter[str(key)] = counter.get(str(key), 0) + amount


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class MetricsStore:
    """Persistent JSON metrics shared across runs (see module docstring)."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.data = self._load()
        self.run: dict[str, Any] = {}

    def _load(self) -> dict[str, Any]:
        if not self.path.exists():
            return _empty_metrics()
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
            if data.get("schema_version") != 1:
                raise ValueError(f"unsupported schema_version {data.get('schema_version')}")
        except (OSError, ValueError) as error:
            backup = self.path.with_name(f"{self.path.stem}.corrupt-{int(time.time())}{self.path.suffix}")
            LOGGER.warning("Metrics file %s unreadable (%s); moved to %s", self.path, error, backup)
            self.path.replace(backup)
            return _empty_metrics()
        base = _empty_metrics()
        for key, value in base.items():
            data.setdefault(key, value)
        for key, value in base["requests"].items():
            data["requests"].setdefault(key, value)
        return data

    def tuning(self, adaptive: bool = True) -> Tuning:
        tuning = Tuning()
        if not adaptive:
            return tuning
        recent = self.data["runs"][-10:]
        requests = sum(run["counters"].get("requests", 0) for run in recent)
        failed = sum(run["counters"].get("failed_requests", 0) for run in recent)
        if requests >= 20:
            rate = failed / requests
            tuning.delay_factor = 1.0 + min(2.0, 3 * rate)
            tuning.backoff_factor = 1.0 + min(3.0, 4 * rate)
            tuning.notes.append(f"recent request failure rate {rate:.1%} over {requests} requests")
        throttled = sum(run.get("errors_by_kind", {}).get(kind, 0) for run in recent[-3:] for kind in THROTTLE_KINDS)
        if throttled:
            tuning.delay_factor *= 1.5
            tuning.backoff_factor *= 1.5
            tuning.notes.append(f"{throttled} throttling responses (429/503) in the last runs: slowing down")
        histogram = self.data["requests"]["attempts_until_success"]
        rescued = sum(count for attempt, count in histogram.items() if int(attempt) > 1)
        exhausted = self.data["requests"]["exhausted"]
        if rescued + exhausted >= 20:
            effectiveness = rescued / (rescued + exhausted)
            tuning.notes.append(f"retries rescued {effectiveness:.0%} of failing requests historically")
            if effectiveness < 0.1:
                tuning.notes.append("retries rarely help; consider a lower --retry")
        tuning.chronic_match_days = {key for key, value in self.data["match_days"].items()
                                     if value.get("consecutive_failures", 0) >= CHRONIC_FAILURES}
        tuning.chronic_leagues = {key for key, value in self.data["leagues"].items()
                                  if value.get("consecutive_failures", 0) >= CHRONIC_FAILURES}
        if tuning.chronic_match_days or tuning.chronic_leagues:
            tuning.notes.append(f"{len(tuning.chronic_match_days)} match day(s) and {len(tuning.chronic_leagues)} "
                                f"league(s) failed in {CHRONIC_FAILURES}+ consecutive runs: retry budget reduced to 1")
        return tuning

    def start_run(self, arguments: dict[str, Any], tuning: Tuning) -> None:
        self.run = {
            "run_id": uuid.uuid4().hex[:12], "started_at": utc_now_iso(), "finished_at": None, "duration_s": None,
            "_started": time.monotonic(), "arguments": arguments, "tuning": tuning.as_dict(),
            "counters": {"requests": 0, "failed_requests": 0, "retries": 0, "successful_downloads": 0,
                         "failed_downloads": 0, "skipped_match_days": 0, "pdf_downloaded": 0, "pdf_failed": 0,
                         "pdf_skipped": 0, "bytes": 0},
            "outcomes": {}, "errors_by_kind": {}, "failures": [],
        }

    def count(self, name: str, amount: int = 1) -> None:
        if self.run:
            _bump(self.run["counters"], name, amount)

    def record_request(self, url: str, attempt: int, ok: bool, kind: str | None, status: int | None,
                       elapsed: float, size: int) -> None:
        requests = self.data["requests"]
        latency = requests["latency_seconds"]
        latency["count"] += 1
        delta = elapsed - latency["mean"]
        latency["mean"] += delta / latency["count"]
        latency["m2"] += delta * (elapsed - latency["mean"])
        latency["min"] = elapsed if latency["min"] is None else min(latency["min"], elapsed)
        latency["max"] = elapsed if latency["max"] is None else max(latency["max"], elapsed)
        host = requests["by_host"].setdefault(urlparse(url).netloc, {"requests": 0, "failures": 0})
        host["requests"] += 1
        self.count("requests")
        self.count("bytes", size)
        if attempt > 1:
            self.count("retries")
        if ok:
            _bump(requests["attempts_until_success"], attempt)
            return
        host["failures"] += 1
        self.count("failed_requests")
        _bump(requests["errors_by_kind"], kind)
        _bump(requests["errors_by_hour_utc"], datetime.now(timezone.utc).hour)
        if status is not None:
            _bump(requests["errors_by_status"], status)
        if self.run:
            _bump(self.run["errors_by_kind"], kind)

    def record_exhausted(self) -> None:
        self.data["requests"]["exhausted"] += 1

    def record_league(self, key: str, outcome: str, error: str | None = None, jornadas: int | None = None) -> None:
        league = self.data["leagues"].setdefault(key, {"consecutive_failures": 0, "outcomes": {}})
        failed = outcome == "failed"
        league["consecutive_failures"] = league["consecutive_failures"] + 1 if failed else 0
        league["last_outcome"], league["last_run_at"] = outcome, utc_now_iso()
        if jornadas is not None:
            league["jornadas"] = jornadas
        if error:
            league["last_error"] = error
        _bump(league["outcomes"], outcome)
        if failed and self.run:
            self.run["failures"].append({"key": key, "error": error})

    def record_match_day(self, key: str, *, url: str, outcome: str, status: str | None, attempts: int,
                         duration: float, errors: list[str]) -> None:
        entry = self.data["match_days"].setdefault(key, {"url": url, "consecutive_failures": 0, "outcomes": {},
                                                         "total_attempts": 0, "history": []})
        failed = outcome in ("failed", "incomplete")
        entry["consecutive_failures"] = entry["consecutive_failures"] + 1 if failed else 0
        entry.update(url=url, last_outcome=outcome, last_run_at=utc_now_iso())
        if status:
            entry["last_status"] = status
        entry["total_attempts"] += attempts
        _bump(entry["outcomes"], outcome)
        entry["history"] = (entry["history"] + [{
            "run_id": self.run.get("run_id"), "at": utc_now_iso(), "outcome": outcome, "status": status,
            "attempts": attempts, "retries": max(0, attempts - 1), "duration_s": round(duration, 3),
            "errors": errors[:10],
        }])[-HISTORY_LIMIT:]
        if not self.run:
            return
        _bump(self.run["outcomes"], outcome)
        if outcome in ("downloaded", "updated", "unchanged", "saved_minimal"):
            self.count("successful_downloads")
        elif failed:
            self.count("failed_downloads")
            if outcome == "failed":
                self.count("skipped_match_days")
            self.run["failures"].append({"key": key, "url": url, "error": errors[-1] if errors else None})
        else:
            self.count("skipped_match_days")

    def finish_run(self) -> dict[str, Any]:
        run = self.run
        run["finished_at"] = utc_now_iso()
        run["duration_s"] = round(time.monotonic() - run.pop("_started"), 3)
        totals = self.data["totals"]
        totals["runs"] += 1
        for name in ("requests", "failed_requests", "retries", "successful_downloads", "failed_downloads",
                     "skipped_match_days"):
            totals[name] += run["counters"][name]
        self.data["runs"] = (self.data["runs"] + [run])[-RUNS_LIMIT:]
        self.data["updated_at"] = run["finished_at"]
        self.save()
        return run

    def save(self) -> None:
        atomic_write(self.path, json.dumps(self.data, ensure_ascii=False, indent=2).encode("utf-8"))


# --------------------------------------------------------------------------- storage


def atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_bytes(data)
    os.replace(temporary, path)


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


def annotate_page(text: str, meta: dict[str, str], matches: list[Match]) -> str:
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


def build_minimal_page(league: League, jornada: int, matches: list[Match], meta: dict[str, str], source_url: str) -> str:
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
            f'<title>{escape(league.label)} - Jornada {jornada}</title>\n</head>\n<body>\n<main>\n'
            f'<h2 class="jornada-results-title"><a href="{escape(source_url)}">Jornada {jornada}</a></h2>\n'
            + "\n".join(blocks) + "\n</main>\n</body>\n</html>\n")


def decode_html(body: bytes, content_type: str = "") -> str:
    charset = re.search(r"charset=([\w-]+)", content_type or "")
    try:
        text = body.decode(charset.group(1) if charset else "utf-8", "replace")
    except LookupError:  # unknown charset name
        text = body.decode("utf-8", "replace")
    if "<html" not in text[:5000].casefold():
        raise RetryableContent("not_html", "response is not an HTML page")
    title = re.search(r"<title[^>]*>(.*?)</title>", text, re.S | re.I)
    if title and BLOCK_TITLE_RE.search(title.group(1)):
        raise RejectedContent("blocked", f"access refused by the server ({title.group(1).strip()[:60]})")
    return text


def pdf_validator(body: bytes, content_type: str) -> None:
    if body.startswith(b"%PDF"):
        if b"%%EOF" not in body[-2048:]:
            raise RetryableContent("truncated_pdf", "PDF is truncated")
        return None
    if "html" in content_type or body.lstrip()[:1] == b"<":
        raise RejectedContent("pdf_unavailable", "acta is not available as PDF")
    raise RetryableContent("invalid_pdf", "response is not a PDF")


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
    matches: list[Match]
    validation: Validation


class IncrementalDownloader:
    def __init__(self, client: HttpClient, metrics: MetricsStore, options: Options, tuning: Tuning | None = None,
                 now: Callable[[], datetime] = now_local) -> None:
        self.client = client
        self.metrics = metrics
        self.options = options
        self.tuning = tuning or Tuning()
        self.now = now

    def _retries_for(self, key: str, chronic: set[str]) -> int:
        if key in chronic and not self.options.force:
            LOGGER.info("%s failed in the last %s runs: only 1 retry", key, CHRONIC_FAILURES)
            return min(1, self.options.retries)
        return self.options.retries

    def run(self) -> bool:
        """Download every selected match day; return False if the index could not be read."""
        try:
            index = self.client.get(INDEX_URL, retries=self.options.retries,
                                    validator=lambda body, ctype: parse_html(decode_html(body, ctype)))
        except FetchError as error:
            LOGGER.error("Competitions index not available, skipping the territory: %s", error)
            self.metrics.record_league("index", "failed", str(error))
            return False
        current, leagues = discover_leagues(index.payload)
        if not leagues:
            LOGGER.error("No TDM / Copa Catalana Femenina leagues found in %s", INDEX_URL)
            self.metrics.record_league("index", "failed", "no leagues found")
            return False
        self.metrics.record_league("index", "ok")
        LOGGER.info("Current season %s: %s league(s) discovered", current or "?", len(leagues))
        if self.options.season and current and self.options.season != current:
            LOGGER.error("Only the current season (%s) can be downloaded incrementally, not %s",
                         current, self.options.season)
            return False
        selected = [league for league in leagues
                    if _matches(self.options.territories, league.territory)
                    and _matches(self.options.categories, league.category, league.gender)
                    and _matches(self.options.groups, league.group)]
        if not selected:
            LOGGER.warning("No league matches the filters")
        for league in selected:
            self.process_league(league)
        return True

    def _had_content(self, league: League) -> bool:
        folder = league.group_folder(self.options.output_dir)
        known = self.metrics.data["leagues"].get(league.key, {}).get("jornadas", 0)
        return bool(known) or (folder.exists() and any(folder.rglob("jornada-*.html")))

    def process_league(self, league: League) -> None:
        LOGGER.info("League %s (%s) -> %s", league.key, league.label, league.url)
        expect_calendar = self._had_content(league)

        def validate(body: bytes, content_type: str) -> tuple[Node, dict[int | None, list[Match]], list[int]]:
            doc = parse_html(decode_html(body, content_type))
            sections, selector = extract_sections(doc), jornada_selector(doc)
            if not any(sections.values()) and (selector or expect_calendar):
                raise RetryableContent("no_calendar", "group page lists no matches")
            return doc, sections, selector

        try:
            result = self.client.get(league.url, retries=self._retries_for(league.key, self.tuning.chronic_leagues),
                                     validator=validate)
        except FetchError as error:
            LOGGER.error("Group page of %s not available, skipping it: %s", league.key, error)
            self.metrics.record_league(league.key, "failed", str(error))
            return
        doc, sections, selector = result.payload
        season, phase = page_heading_info(doc)
        if season and season != league.season:
            LOGGER.warning("%s page shows season %s (index suggested %s)", league.key, season, league.season or "?")
            league.season = season
        if not league.season:
            LOGGER.error("Season of %s unknown (neither the index nor the page names it); skipping", league.label)
            self.metrics.record_league(league.key, "failed", "unknown season")
            return
        if self.options.season and league.season != self.options.season:
            LOGGER.info("Skipping %s: season %s not requested", league.key, league.season)
            return
        league.phase = phase or DEFAULT_PHASE
        if not _matches(self.options.phases, league.phase):
            return
        calendar = {number: matches for number, matches in sections.items() if number is not None}
        jornadas = sorted(set(calendar) | set(selector))
        if not jornadas:
            LOGGER.info("%s has no published calendar yet", league.key)
            self.metrics.record_league(league.key, "not_started")
            return
        self.metrics.record_league(league.key, "ok", jornadas=len(jornadas))
        for jornada in jornadas:
            if self.options.match_days is None or jornada in self.options.match_days:
                self.process_match_day(league, jornada, calendar.get(jornada, []))

    def _match_day_validator(self, jornada: int, expected: list[Match], future: bool):
        def validate(body: bytes, content_type: str) -> PageCheck:
            text = decode_html(body, content_type)
            sections = extract_sections(parse_html(text))
            matches = sections.get(jornada)
            if matches is None:
                listed = sorted(number for number in sections if number is not None)
                if listed:
                    raise RetryableContent("wrong_match_day", f"page lists jornada(s) {listed} instead of {jornada}")
                matches = sections.get(None, [])
            validation = validate_matches(matches, expected)
            if validation.status == "empty" and not future:
                raise RetryableContent("no_results", "no results although the match day has already started")
            if validation.status == "invalid":
                raise RetryableContent("incomplete_content", "; ".join(validation.errors[:5]),
                                       PageCheck(text, matches, validation) if validation.salvageable else None)
            return PageCheck(text, matches, validation)

        return validate

    def process_match_day(self, league: League, jornada: int, calendar: list[Match]) -> None:
        key, url = league.match_day_key(jornada), league.page_url(jornada)
        destination = league.html_path(self.options.output_dir, jornada)
        future = is_future(calendar, self.now())
        calendar_fp = fingerprint([match.calendar_summary() for match in calendar])
        saved = read_saved_meta(destination)
        started = time.monotonic()

        def record(outcome: str, status: str | None, attempts: int, errors: list[str] | None = None) -> None:
            self.metrics.record_match_day(key, url=url, outcome=outcome, status=status, attempts=attempts,
                                          duration=time.monotonic() - started, errors=errors or [])

        if not self.options.force and future and destination.is_file() and destination.stat().st_size > 0:
            # Nothing has been played yet and a version is already saved: keep it (use --force to refresh).
            LOGGER.debug("Future match day already downloaded: %s (%s)", key, saved.get("acta-content-status"))
            record("up_to_date", saved.get("acta-content-status"), 0)
            return

        if not self.options.force and saved.get("acta-calendar-fingerprint") == calendar_fp and (
                saved.get("acta-content-status") == "complete"
                or (saved.get("acta-content-status") == "partial" and future)):
            LOGGER.debug("Up to date: %s (%s)", key, saved.get("acta-content-status"))
            if self.options.want_pdf:
                saved_sections = extract_sections(parse_html(destination.read_text(encoding="utf-8", errors="replace")))
                self.download_pdfs(league, jornada, saved_sections.get(jornada) or saved_sections.get(None, []))
            record("up_to_date", saved.get("acta-content-status"), 0)
            return

        try:
            result = self.client.get(url, retries=self._retries_for(key, self.tuning.chronic_match_days),
                                     validator=self._match_day_validator(jornada, calendar, future))
        except FetchError as error:
            if isinstance(error.payload, PageCheck):
                # The page lists the expected matches, but some actas stay incomplete after every retry
                # (typically players without codi_jugador link). Keep it as partial so it is retried next run.
                incomplete: PageCheck = error.payload
                outcome = self._save(league, jornada, destination, incomplete.matches, "partial", "match_day_page",
                                     calendar_fp, url, saved, incomplete.text)
                LOGGER.error("%s still incomplete after %s attempt(s), saved as partial (%s): %s",
                             key, error.attempts, outcome, error.message)
                if self.options.want_pdf:
                    self.download_pdfs(league, jornada, incomplete.matches)
                record("incomplete", "partial", error.attempts, [error.message])
                return
            LOGGER.error("Skipping %s: %s", key, error)
            if future and calendar and self.options.want_html:
                self._save(league, jornada, destination, calendar, "partial", "calendar", calendar_fp, url, saved)
                LOGGER.info("Saved minimal match info of future %s from the calendar", key)
            record("failed", None, error.attempts, [error.message])
            return

        page: PageCheck = result.payload
        for warning in page.validation.warnings:
            LOGGER.info("%s: %s", key, warning)
        if page.validation.status == "empty":
            if not calendar:
                LOGGER.info("%s is a future match day without matches listed anywhere; nothing to save", key)
                record("no_matches", None, result.attempts)
                return
            outcome = self._save(league, jornada, destination, calendar, "partial", "calendar", calendar_fp, url, saved)
            LOGGER.info("%s not played yet: saved minimal match info from the calendar (%s)", key, outcome)
            record("saved_minimal" if outcome != "unchanged" else outcome, "partial", result.attempts)
            return
        status = page.validation.status
        outcome = self._save(league, jornada, destination, page.matches, status, "match_day_page", calendar_fp, url,
                             saved, page.text)
        LOGGER.info("%s: %s (%s, %s match(es), %s attempt(s))", key, outcome, status, len(page.matches), result.attempts)
        if self.options.want_pdf:
            self.download_pdfs(league, jornada, page.matches)
        record(outcome, status, result.attempts)

    def _save(self, league: League, jornada: int, destination: Path, matches: list[Match], status: str, origin: str,
              calendar_fp: str, url: str, saved: dict[str, str], page_text: str | None = None) -> str:
        content_fp = fingerprint([match.content_summary() for match in matches])
        if (not self.options.force and saved.get("acta-content-fingerprint") == content_fp
                and saved.get("acta-content-status") == status
                and saved.get("acta-calendar-fingerprint") == calendar_fp):
            return "unchanged"
        if not self.options.want_html:
            return "downloaded"
        meta = {
            "acta-content-status": status, "acta-content-origin": origin, "acta-season": league.season,
            "acta-territory": league.territory, "acta-gender": league.gender, "acta-category": league.category,
            "acta-group": league.group, "acta-phase": league.phase, "acta-match-day": str(jornada),
            "acta-matches": str(len(matches)), "acta-matches-played": str(sum(m.played for m in matches)),
            "acta-matches-incomplete": str(sum(m.content_status == "incomplete" for m in matches)),
            "acta-source-url": url, "acta-downloaded-at": utc_now_iso(),
            "acta-content-fingerprint": content_fp, "acta-calendar-fingerprint": calendar_fp,
        }
        if page_text is None:
            content = build_minimal_page(league, jornada, matches, meta, url)
        else:
            content = annotate_page(page_text, meta, matches)
        atomic_write(destination, content.encode("utf-8"))
        return "updated" if saved else "downloaded"

    def download_pdfs(self, league: League, jornada: int, matches: list[Match]) -> None:
        folder = league.pdf_dir(self.options.output_dir, jornada)
        for match in matches:
            if not match.played or not match.acta_url:
                continue
            path = folder / f"{match.match_id}.pdf"
            if not self.options.force and path.exists() and path.read_bytes()[:4] == b"%PDF":
                self.metrics.count("pdf_skipped")
                continue
            try:
                result = self.client.get(match.acta_url, retries=self.options.retries, accept=PDF_ACCEPT,
                                         validator=pdf_validator)
            except FetchError as error:
                LOGGER.error("PDF acta %s of %s not downloaded: %s", match.match_id, league.match_day_key(jornada), error)
                self.metrics.count("pdf_failed")
                continue
            atomic_write(path, result.body)
            self.metrics.count("pdf_downloaded")
            LOGGER.info("PDF acta saved: %s", path)


def _matches(wanted: set[str] | None, *values: str) -> bool:
    return wanted is None or any(slugify(value) in wanted for value in values)


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


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
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
    parser.add_argument("--request_delay", type=float, default=3.0, help="Minimum seconds between requests (default: 3)")
    parser.add_argument("--backoff", type=float, default=5.0, help="Base seconds of the exponential backoff (default: 5)")
    parser.add_argument("--timeout", type=float, default=30.0, help="Request timeout in seconds (default: 30)")
    parser.add_argument("--no_adaptive", action="store_true", help="Ignore previous metrics when tuning retries")
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
    if args.retry < 0 or args.request_delay < 0 or args.backoff < 0 or args.timeout <= 0:
        parser.error("--retry, --request_delay and --backoff must be >= 0 and --timeout > 0")
    if args.season and not re.fullmatch(r"\d{4}-\d{4}", args.season):
        parser.error("--season must look like 2026-2027")
    skip_pdf = args.skip_pdf if args.skip_pdf is not None else args.format != "pdf"
    configure_logging(args.log_file, args.verbose)

    metrics = MetricsStore(args.metrics_file)
    tuning = metrics.tuning(adaptive=not args.no_adaptive)
    for note in tuning.notes:
        LOGGER.info("Metrics: %s", note)
    options = Options(
        output_dir=args.output_dir, season=args.season, territories=parse_filter(args.territory),
        categories=parse_filter(args.category), groups=parse_filter(args.group), phases=parse_filter(args.phase),
        match_days=args.match_day, want_html=args.format in ("html", "both"),
        want_pdf=args.format in ("pdf", "both") and not skip_pdf, force=args.force, retries=args.retry,
    )
    client = HttpClient(metrics, request_delay=args.request_delay * tuning.delay_factor, timeout=args.timeout,
                        backoff_base=args.backoff * tuning.backoff_factor)
    metrics.start_run({key: (sorted(value) if isinstance(value, set) else str(value) if isinstance(value, Path) else value)
                       for key, value in vars(args).items()}, tuning)
    ok = False
    try:
        ok = IncrementalDownloader(client, metrics, options, tuning).run()
    except KeyboardInterrupt:
        LOGGER.warning("Interrupted; saving metrics")
    finally:
        run = metrics.finish_run()
    counters = run["counters"]
    LOGGER.info("Done in %.1f s: %s downloaded, %s failed, %s skipped, %s retries, %s requests; outcomes %s",
                run["duration_s"], counters["successful_downloads"], counters["failed_downloads"],
                counters["skipped_match_days"], counters["retries"], counters["requests"], run["outcomes"])
    if options.want_pdf:
        LOGGER.info("PDF actas: %s downloaded, %s already present, %s failed",
                    counters["pdf_downloaded"], counters["pdf_skipped"], counters["pdf_failed"])
    if not ok:
        return 2
    return 1 if counters["failed_downloads"] else 0


if __name__ == "__main__":
    sys.exit(main())
