"""Download model "validated pages" (BCNESA): every match day page is validated on its own and rebuilt.

A match day page is accepted when it lists as many matches as the league page, every played match has its results
table (whose running score ends at the final score) with all players linked by ``codi_jugador`` and every unplayed
match has date, time, teams and venue. Pages that fail are retried; after the last retry the best page is saved as
``partial`` with its issues in a meta tag. The saved document is rebuilt from the match containers (with absolute
links), so its text, not the served page, is what is stored.

A jornada whose saved HTML is ``complete`` (and whose PDFs exist) is not requested again; a future jornada (no
match started yet) that is already saved is not refreshed either. A future jornada is never requested: the league
page already lists its minimal match info.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import logging
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, cast
from urllib.parse import parse_qs, urljoin, urlparse

from bs4 import BeautifulSoup, Tag

from ingest_common import progress
from ingest_common.region import region_values
from ingest_fcttweb import clock
from ingest_fcttweb.discovery import League, discover, phase_from_text
from ingest_fcttweb.files import atomic_write
from ingest_fcttweb.http import ContentRejected, FetchClient, Fetched
from ingest_fcttweb.logging_setup import configure_script_logger
from ingest_fcttweb.markup import (
    Reader, SCORE_RE, clean_name, day_sections, fold, league_page_title, make_soup, normalise_group, selector_days,
)
from ingest_fcttweb.metrics import RequestLogMetrics
from ingest_fcttweb.profile import INDEX_URL, SiteProfile

RATE_LIMIT_FACTORS = (2.0, 1.5)


@dataclass(frozen=True)
class DayMatch:
    match_id: str
    played: bool
    acta_url: str | None


@dataclass
class ScheduledDay:
    """A jornada as listed on the league page: its heading and match containers."""

    heading: Tag | None
    containers: list[Tag]


# --------------------------------------------------------------------------- arguments


def current_season(today: Any = None) -> str:
    today = today or clock.local_now().date()
    start = today.year if today.month >= 8 else today.year - 1
    return f"{start}-{start + 1}"


def parse_match_days(value: str) -> set[int]:
    days: set[int] = set()
    for part in filter(None, (piece.strip() for piece in value.split(","))):
        bounds = re.fullmatch(r"(\d+)(?:\s*-\s*(\d+))?", part)
        if not bounds:
            raise argparse.ArgumentTypeError(f"invalid match day: {part!r} (use 3, 1,4 or 2-5)")
        first, last = int(bounds.group(1)), int(bounds.group(2) or bounds.group(1))
        days.update(range(min(first, last), max(first, last) + 1))
    return days


def build_parser(profile: SiteProfile, description: str | None = None) -> argparse.ArgumentParser:
    http = profile.http
    parser = argparse.ArgumentParser(description=description or __doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--season", default=current_season(), help="Season, e.g. 2026-2027 (default: current season)")
    parser.add_argument("--territory", help=f"Territory: {', '.join(profile.sections.territories)} (default: all)")
    parser.add_argument("--region_type", help="Region type: provincial or comarcal (default: all)")
    parser.add_argument("--category", help="Category, e.g. 'RTB PREFERENT' or 'PREFERENT' (default: all)")
    parser.add_argument("--group", help="Group, e.g. G1 or 1 (default: all)")
    parser.add_argument("--phase", help="Phase, e.g. '1a Fase' (default: all)")
    parser.add_argument("--match_day", type=parse_match_days, help="Match day(s): 3, 1,4 or 2-5 (default: all)")
    parser.add_argument("--format", choices=("html", "pdf", "both"), default="both", help="Content to download (default: both)")
    parser.add_argument("--output_dir", type=Path, required=True, help=f"Output directory (<data_dir>/{profile.name}/content)")
    parser.add_argument("--log_file", type=Path, required=True, help="Log file")
    parser.add_argument("--force", action="store_true", help="Re-download content even if it already exists")
    parser.add_argument("--retry", type=int, default=3, help="Retries for failed downloads (default: 3)")
    parser.add_argument(
        "--skip_pdf", action=argparse.BooleanOptionalAction, default=True,
        help="Skip downloading PDF actas (default: on; use --no-skip_pdf to download them)",
    )
    parser.add_argument("--delay", type=float, default=http.delay,
                        help=f"Minimum seconds between requests (default: {http.delay})")
    parser.add_argument("--backoff", type=float, default=http.backoff,
                        help=f"Base seconds for exponential retry backoff (default: {http.backoff})")
    parser.add_argument("--timeout", type=float, default=http.timeout, help=f"Request timeout in seconds (default: {http.timeout:g})")
    parser.add_argument(
        "--metrics_file", type=Path, required=True,
        help="Success/failure metrics JSON, read and updated on each run",
    )
    parser.add_argument(
        "--adaptive", action=argparse.BooleanOptionalAction, default=True,
        help="Tune retries and backoff from the metrics of previous runs (default: on)",
    )
    return parser


def parse_args(profile: SiteProfile, argv: list[str] | None = None) -> argparse.Namespace:
    parser = build_parser(profile)
    args = parser.parse_args(argv)
    if not re.fullmatch(r"\d{4}-\d{4}", args.season):
        parser.error("--season must look like 2026-2027")
    if args.retry < 0:
        parser.error("--retry must be >= 0")
    return args


# --------------------------------------------------------------------------- content checks


def html_check(*selectors: str) -> Callable[[bytes, str], BeautifulSoup]:
    """Accept an HTML response only when it contains one of ``selectors``."""

    def check(body: bytes, content_type: str) -> BeautifulSoup:
        soup = make_soup(body)
        if not any(soup.select_one(selector) for selector in selectors):
            raise ContentRejected("empty_page", "page without the expected content (the site returned an empty page)")
        return soup

    return check


def pdf_check(body: bytes, content_type: str) -> bytes:
    if not body.lstrip()[:5].startswith(b"%PDF"):
        raise ContentRejected("not_pdf", f"response is not a PDF ({content_type or 'no content type'}, {len(body)} bytes)")
    return body


def jornada_check(reader: Reader, page_url: str, expected: int | None,
                  tolerate_incomplete: bool) -> Callable[[bytes, str], BeautifulSoup]:
    """Accept a jornada page with matches; reject (to retry) one that fails validation."""

    def check(body: bytes, content_type: str) -> BeautifulSoup:
        soup = make_soup(body)
        containers = soup.select(".match-container")
        if not containers:
            raise ContentRejected("empty_page", "page without matches (the site returned an empty page)")
        issues = validate_day(reader, containers, parse_matches(reader, containers, page_url), expected)
        if issues and not tolerate_incomplete:
            raise ContentRejected(
                "incomplete_content",
                f"incomplete content: {issues[0]}" + (f" (+{len(issues) - 1} more)" if len(issues) > 1 else ""),
                payload=soup)
        return soup

    return check


# --------------------------------------------------------------------------- league page


def page_season(soup: BeautifulSoup) -> str | None:
    main = soup.find("main")
    for css_class in (main.get("class") or []) if isinstance(main, Tag) else []:
        match = re.fullmatch(r"temporades-(\d{4}-\d{4})", css_class)
        if match:
            return match.group(1)
    title = soup.select_one(".br-apic-league-title")
    match = re.search(r"(\d{4})\s*/\s*(\d{4})", title.get_text(" ", strip=True)) if title else None
    return f"{match.group(1)}-{match.group(2)}" if match else None


def match_days(soup: BeautifulSoup, reader: Reader) -> list[int]:
    sections, _ = day_sections(soup, reader)
    return sorted(set(selector_days(soup)) | {number for number in sections if number is not None})


def league_schedule(soup: BeautifulSoup, reader: Reader) -> dict[int, ScheduledDay]:
    sections, headings = day_sections(soup, reader)
    return {number: ScheduledDay(headings.get(number), containers)
            for number, containers in sections.items() if number is not None}


def match_datetime(reader: Reader, container: Tag) -> datetime | None:
    """Parse a match container's ``dd/mm/yyyy · hh:mm`` date (time optional)."""
    found = re.search(r"(\d{1,2})/(\d{1,2})/(\d{4})(?:\D+(\d{1,2}):(\d{2}))?", reader.text(container.select_one(".match-datetime")))
    if not found:
        return None
    day, month, year, hour, minute = found.groups()
    try:
        return datetime(int(year), int(month), int(day), int(hour or 0), int(minute or 0))
    except ValueError:
        return None


def is_future_day(reader: Reader, scheduled: ScheduledDay | None, now: datetime | None = None) -> bool:
    """True when every match of the jornada is dated after ``now`` (none has started yet)."""
    if not scheduled or not scheduled.containers:
        return False
    now = now or clock.local_now()
    dates = [match_datetime(reader, container) for container in scheduled.containers]
    return all(value is not None and value > now for value in dates)


# --------------------------------------------------------------------------- jornada content


def parse_matches(reader: Reader, containers: list[Tag], page_url: str) -> list[DayMatch]:
    matches = []
    for position, container in enumerate(containers, start=1):
        acta = container.select_one("a.acta-link[href]") or next(
            (anchor for anchor in container.find_all("a", href=True)
             if re.search(r"/imprimir/acta|\.pdf(?:$|[?#])", cast(str, anchor["href"]), re.I)),
            None,
        )
        acta_url = urljoin(page_url, cast(str, acta["href"])) if acta else None
        played = bool(
            acta_url or container.select_one(".match-results-table")
            or SCORE_RE.search(reader.text(container.select_one(".match-score")))
        )
        partido = re.search(r"/partido/(\d+)", acta_url or "")
        team_ids = [
            found.group(1)
            for anchor in container.select(".team-home a[href], .team-away a[href]")
            if (found := re.search(r"team_name_id=(\d+)", cast(str, anchor["href"])))
        ]
        match_id = partido.group(1) if partido else ("-".join(team_ids) if len(team_ids) == 2 else str(position))
        matches.append(DayMatch(clean_name(match_id), played, acta_url))
    return matches


def is_complete(matches: list[DayMatch], issues: list[str]) -> bool:
    return bool(matches) and not issues and all(match.played and match.acta_url for match in matches)


def venue(reader: Reader, container: Tag) -> str:
    text = reader.text(container.select_one(".match-info, .field-info"))
    text = re.sub(r"descarregar acta.*$", "", text, flags=re.I)
    return re.sub(r"^\s*terreny de joc\s*:?", "", text, flags=re.I).strip()


def validate_players(reader: Reader, row: Tag, label: str) -> list[str]:
    """Every player of a results row must be linked with a numeric ``codi_jugador`` and a name."""
    issues = []
    cells = row.select("td.player-info")
    if len(cells) != 2:
        return [f"{label}: expected 2 player cells, found {len(cells)}"]
    for side, cell in zip(("left", "right"), cells):
        anchors = cell.find_all("a")
        if not anchors:
            text = reader.text(cell)
            issues.append(f"{label} {side}: player {text!r} has no player link" if text else f"{label} {side}: no player")
            continue
        for anchor in anchors:
            query = parse_qs(urlparse(cast(str, anchor.get("href", ""))).query)
            code = (query.get("codi_jugador") or [""])[0]
            name = reader.text(anchor)
            if not code.isdigit():
                issues.append(f"{label} {side}: player {name!r} without a codi_jugador link")
            if not name:
                issues.append(f"{label} {side}: player {code or '?'} without a name")
            listed = (query.get("jugador_nom") or [""])[0]
            if name and listed and fold(listed) != fold(name):
                issues.append(f"{label} {side}: player name {name!r} differs from the linked {listed!r}")
    return issues


def validate_match(reader: Reader, container: Tag, match: DayMatch) -> list[str]:
    """Validate one match: full acta when played, minimal info (date, time, teams, venue) otherwise."""
    label = f"match {match.match_id}"
    issues = []
    home, away = reader.text(container.select_one(".team-home")), reader.text(container.select_one(".team-away"))
    if not home or not away:
        issues.append(f"{label}: missing team names")
    if not match.played:
        when = reader.text(container.select_one(".match-datetime"))
        if not match_datetime(reader, container):
            issues.append(f"{label}: missing date")
        elif not re.search(r"\d{1,2}:\d{2}", when):
            issues.append(f"{label}: missing time")
        if not venue(reader, container):
            issues.append(f"{label}: missing venue")
        return issues

    table = container.select_one(".match-results-table")
    rows = [row for row in table.select("tr") if row.select_one("td.position")] if table else []
    if not rows:
        score = reader.text(container.select_one(".match-score")) or "no score"
        return issues + [f"{label}: played ({score}) but without its results table"]
    for number, row in enumerate(rows, start=1):
        issues += validate_players(reader, row, f"{label} game {number}")
    final = SCORE_RE.search(reader.text(container.select_one(".match-score")))
    running = [SCORE_RE.search(reader.text(row.select_one("td.global"))) for row in rows]
    last = next((score for score in reversed(running) if score), None)
    if final and last:
        final_score, last_score = final.groups(), last.groups()
        if last_score not in (final_score, final_score[::-1]):
            issues.append(f"{label}: results table ends at {'-'.join(last_score)}, final score is {'-'.join(final_score)}")
    return issues


def validate_day(reader: Reader, containers: list[Tag], matches: list[DayMatch], expected: int | None) -> list[str]:
    issues = []
    if expected is not None and len(containers) != expected:
        issues.append(f"{len(containers)} matches listed, the league page schedules {expected}")
    for container, match in zip(containers, matches):
        issues += validate_match(reader, container, match)
    return issues


# --------------------------------------------------------------------------- storage


class Downloader:
    """Downloads the match days of the selected leagues of a "validated pages" profile."""

    def __init__(self, profile: SiteProfile, args: argparse.Namespace, client: FetchClient,
                 metrics: RequestLogMetrics, logger: logging.Logger):
        self.profile = profile
        self.layout = profile.layout
        self.meta = profile.layout.meta
        self.reader = Reader(profile.text_mode)
        self.args = args
        self.client = client
        self.metrics = metrics
        self.logger = logger

    # ---- files

    def region_meta(self, category: str) -> dict[str, str]:
        """``region`` / ``region-type`` meta tags, left out when the category prefix is unknown (the parser fails)."""
        values = region_values(self.profile.source, category)
        return dict(zip((self.meta.region, self.meta.region_type), values)) if values else {}

    def build_document(self, league: League, phase: str, day: int, page_url: str, heading: str,
                       fragments: list[Tag], complete: bool, content_source: str,
                       issues: list[str]) -> tuple[bytes, str]:
        """Return the jornada HTML document and the hash of its match content."""
        for fragment in fragments:
            for anchor in fragment.find_all("a", href=True):
                anchor["href"] = urljoin(page_url, cast(str, anchor["href"]))
        body = "\n".join(str(fragment) for fragment in fragments)
        content_hash = hashlib.sha256(body.encode("utf-8")).hexdigest()
        meta = {
            self.meta.status: "complete" if complete else "partial",
            cast(str, self.meta.content_hash): content_hash,
            cast(str, self.meta.issues): " | ".join(issues),
            self.meta.season: self.args.season,
            self.meta.territory: league.territory,
            **self.region_meta(league.category),
            self.meta.category: league.category,
            self.meta.group: league.group,
            self.meta.phase: phase,
            self.meta.match_day: str(day),
            self.meta.source_url: page_url,
            # "jornada-page", or "league-page" for a future jornada (minimal match info only).
            cast(str, self.meta.content_source): content_source,
            self.meta.downloaded_at: clock.local_iso(),
        }
        title = f"{league.category} / {league.group} / {phase} / Jornada {day}"
        document = "\n".join([
            "<!DOCTYPE html>",
            '<html lang="ca"><head><meta charset="utf-8">',
            *(f'<meta name="{name}" content="{html.escape(value)}">' for name, value in meta.items()),
            f"<title>{html.escape(title)}</title></head>",
            "<body>",
            f"<h1>{html.escape(heading or league.title)}</h1>",
            f'<section class="jornada" data-jornada="{day}">',
            body,
            "</section>",
            "</body></html>",
            "",
        ])
        return document.encode("utf-8"), content_hash

    def read_saved(self, path: Path) -> tuple[BeautifulSoup | None, dict[str, str]]:
        return read_saved(path)

    def missing_pdfs(self, day_dir: Path, matches: list[DayMatch]) -> list[DayMatch]:
        """Played matches with a PDF acta that is not downloaded yet."""
        return [
            match for match in matches
            if match.played and match.acta_url
            and not ((day_dir / f"{match.match_id}.pdf").exists() and (day_dir / f"{match.match_id}.pdf").stat().st_size > 0)
        ]

    # ---- processing

    def process_match_day(self, league: League, phase: str, day: int, league_dir: Path,
                          scheduled: ScheduledDay | None, league_title: str) -> None:
        args, logger, metrics = self.args, self.logger, self.metrics
        label = f"{league.territory} / {league.category} / {league.group} / {phase} / J{day}"
        key = f"{args.season}/{league.category}/{league.group}/{phase}/J{day}"
        want_html = args.format in ("html", "both")
        want_pdf = args.format in ("pdf", "both") and not args.skip_pdf
        html_file = league_dir / self.layout.html_name.format(day=day)
        day_dir = league_dir / self.layout.pdf_folder.format(day=day)
        started = time.monotonic()

        saved_soup, saved_meta = self.read_saved(html_file)
        if not args.force and saved_soup and saved_meta.get(self.meta.status) == "complete":
            saved_matches = parse_matches(self.reader, saved_soup.select(".match-container"),
                                          saved_meta.get(self.meta.source_url, league.url))
            if not want_pdf or not self.missing_pdfs(day_dir, saved_matches):
                logger.info("%s: complete, skipped", label)
                metrics.record_match_day(key, "skipped")
                return
        if not args.force and saved_soup and is_future_day(self.reader, scheduled):
            # Nothing has been played yet and a version is already saved: keep it (use --force to refresh).
            logger.info("%s: future match day already downloaded, skipped", label)
            metrics.record_match_day(key, "skipped")
            return

        page_url = f"{league.url.rstrip('/')}/?jornada={day}"
        expected = len(scheduled.containers) if scheduled and scheduled.containers else None
        retries = 0
        if is_future_day(self.reader, scheduled):
            # Nothing has been played yet, so there are no results to wait for: the league page
            # already lists the minimal match info and the jornada page is neither requested nor retried.
            scheduled = cast(ScheduledDay, scheduled)
            containers, heading, content_source, title = scheduled.containers, scheduled.heading, "league-page", league_title
            logger.info("%s: future match day, keeping minimal match info from the league page", label)
        else:
            tolerate = self.args.adaptive and not args.force and metrics.persistently_incomplete(key)
            if tolerate:
                logger.info("%s: same incomplete content in the last runs, validation issues are not retried", label)
            fetched = self.client.get(page_url, jornada_check(self.reader, page_url, expected, tolerate),
                                      kind="jornada", key=key)
            soup = fetched.payload if fetched.outcome in ("ok", "incomplete") else None
            retries = fetched.retries
            if soup is None:
                logger.error("%s: match day not available after retries, skipped (%s)", label, page_url)
                metrics.record_match_day(key, "failed", retries, time.monotonic() - started,
                                         fetched.errors[-1] if fetched.errors else fetched.outcome)
                return
            containers = soup.select(".match-container")
            heading = soup.select_one(".jornada-results-title")
            content_source, title = "jornada-page", league_page_title(soup) or league_title
            if heading and not re.search(rf"\b{day}\b", heading.get_text(" ", strip=True)):
                logger.warning("%s: page heading %r does not match the requested jornada", label, heading.get_text(" ", strip=True))

        matches = parse_matches(self.reader, containers, page_url)
        issues = validate_day(self.reader, containers, matches, expected)
        complete = is_complete(matches, issues)
        for issue in issues:
            logger.error("%s: validation: %s", label, issue)
        played = sum(match.played for match in matches)
        outcome = "future" if content_source == "league-page" else "incomplete" if issues else "unchanged"

        if want_html:
            fragments = ([heading] if heading else []) + containers
            document, content_hash = self.build_document(
                league, phase, day, page_url, title, fragments, complete, content_source, issues)
            status = "complete" if complete else "partial"
            if (not args.force and saved_meta.get(cast(str, self.meta.content_hash)) == content_hash
                    and saved_meta.get(self.meta.status) == status):
                logger.info("%s: unchanged (%d matches, %d played)", label, len(matches), played)
            else:
                atomic_write(html_file, document)
                if outcome == "unchanged":
                    outcome = "updated" if saved_soup else "new"
                logger.info("%s: saved %s (%d matches, %d played, %s)", label, html_file, len(matches), played, status)
        metrics.record_match_day(key, outcome, retries, time.monotonic() - started, issues=issues)

        if want_pdf:
            # Guardrail: only played matches with a published acta link get a PDF.
            targets = [m for m in matches if m.played and m.acta_url] if args.force else self.missing_pdfs(day_dir, matches)
            for match in targets:
                fetched = self.client.get(cast(str, match.acta_url), pdf_check, kind="pdf", key=f"{key}/{match.match_id}")
                if not fetched.ok:
                    logger.error("%s: PDF acta %s not available, skipped (%s)", label, match.match_id, match.acta_url)
                    metrics.count("pdfs failed")
                    continue
                atomic_write(day_dir / f"{match.match_id}.pdf", fetched.payload)
                metrics.count("pdfs downloaded")
                logger.info("%s: saved %s", label, day_dir / f"{match.match_id}.pdf")

    def process_league(self, league: League) -> None:
        args, logger, metrics = self.args, self.logger, self.metrics
        label = f"{league.territory} / {league.title}"
        fetched = self.client.get(league.url, html_check(".jornada-btn", ".jornada-results-title"),
                                  kind="league", key=league.url)
        soup = fetched.payload if fetched.ok else None
        if soup is None:
            logger.error("%s: league page without match days after retries, skipped (%s)", label, league.url)
            metrics.count("leagues failed")
            return
        season = page_season(soup)
        if season and season != args.season:
            logger.warning("%s: page is for season %s, not %s; skipped", label, season, args.season)
            metrics.count("leagues skipped (season)")
            return
        phase = league.phase or phase_from_text(league_page_title(soup))[0] or self.layout.default_phase
        if args.phase and fold(phase) != fold(args.phase):
            return
        days = match_days(soup, self.reader)
        if args.match_day:
            days = [day for day in days if day in args.match_day]
        if not days:
            logger.info("%s: no match days to download", label)
            return
        metrics.count("leagues processed")
        league_dir = (
            args.output_dir / clean_name(args.season) / clean_name(league.category)
            / clean_name(league.group) / clean_name(phase)
        )
        schedule = league_schedule(soup, self.reader)
        logger.info("%s: %d match day(s) -> %s", label, len(days), league_dir)
        for day in days:
            try:
                self.process_match_day(league, phase, day, league_dir, schedule.get(day), league_page_title(soup))
            except Exception as exc:  # Keep processing the remaining match days.
                metrics.record_match_day(f"{args.season}/{league.category}/{league.group}/{phase}/J{day}", "failed",
                                         error=f"unexpected error: {exc}")
                logger.exception("%s / J%d: unexpected error: %s", label, day, exc)

    # ---- filters

    def league_matches_filters(self, league: League) -> bool:
        args = self.args
        if args.territory and fold(league.territory) != fold(args.territory):
            return False
        if getattr(args, "region_type", None):
            values = region_values(self.profile.source, league.category)
            if values is None or values[1] != args.region_type.strip().lower():
                return False
        if args.category:
            wanted = fold(args.category)
            short = re.sub(r"^rt[bglt] ", "", fold(league.category))
            if wanted not in {fold(league.category), short, fold(league.title)}:
                return False
        if args.group and league.group != normalise_group(args.group):
            return False
        return True

    def run(self) -> int:
        args, logger, metrics = self.args, self.logger, self.metrics
        logger.info("Downloading actas content for season %s (format=%s, skip_pdf=%s, force=%s)",
                    args.season, args.format, args.skip_pdf, args.force)
        if args.season != current_season():
            logger.warning("fctt.cat only lists the current season (%s); leagues from other seasons are skipped", current_season())
        index = self.client.get(INDEX_URL, html_check(".jet-engine-listing-overlay-wrap"), kind="index")
        if not index.ok:
            logger.error("Could not load the league index %s; every territory is skipped", INDEX_URL)
            metrics.count("index failed")
            return 2
        leagues = [league for league in discover(self.profile, index.payload, INDEX_URL).leagues
                   if self.league_matches_filters(league)]
        if not leagues:
            logger.error("No league matches the territory/category/group filters")
            return 2
        logger.info("Found %d league(s) to process", len(leagues))
        progress.total(len(leagues))

        for league in leagues:
            try:
                self.process_league(league)
            except Exception as exc:  # Keep processing the remaining leagues.
                metrics.count("leagues failed")
                logger.exception("%s / %s: unexpected error: %s", league.territory, league.title, exc)
            progress.item(f"{league.territory} {league.title}")
        return 0


def read_saved(path: Path) -> tuple[BeautifulSoup | None, dict[str, str]]:
    if not path.exists() or path.stat().st_size == 0:
        return None, {}
    soup = make_soup(path.read_bytes())
    meta = {cast(str, tag["name"]): cast(str, tag.get("content", "")) for tag in soup.find_all("meta", attrs={"name": True})}
    return soup, meta


def retry_policy(args: argparse.Namespace, metrics: RequestLogMetrics, logger: logging.Logger) -> dict[str, Any]:
    """Retries per request kind and backoff for this run, learnt from previous runs' metrics."""
    policy: dict[str, Any] = {"adaptive": args.adaptive, "retries": {}, "backoff": args.backoff, "delay": args.delay}
    for kind in ("index", "league", "jornada", "pdf"):
        retries = metrics.advised_retries(kind, args.retry) if args.adaptive else args.retry
        policy["retries"][kind] = retries
        estimate = metrics.retry_success_rate(kind)
        if args.adaptive and estimate:
            logger.info("Adaptive retries for %s pages: %d (%.0f%% of %d past retries succeeded)",
                        kind, retries, estimate[0] * 100, estimate[1])
    if args.adaptive and metrics.rate_limited():
        policy["backoff"], policy["delay"] = args.backoff * RATE_LIMIT_FACTORS[0], args.delay * RATE_LIMIT_FACTORS[1]
        logger.info("Recent failures are mostly rate limiting: backoff %.1fs, delay %.1fs", policy["backoff"], policy["delay"])
    return policy


def main(profile: SiteProfile, argv: list[str] | None = None) -> int:
    args = parse_args(profile, argv)
    logger = configure_script_logger(profile.download_logger, args.log_file)
    metrics = RequestLogMetrics(args.metrics_file, logger)
    policy = retry_policy(args, metrics, logger)
    for line in metrics.problem_report():
        logger.info("Known problem: %s", line)
    client = FetchClient(profile.http, metrics, retries=policy["retries"], default_retries=args.retry,
                         delay=policy["delay"], backoff=policy["backoff"], timeout=args.timeout, logger=logger)
    downloader = Downloader(profile, args, client, metrics, logger)
    try:
        code = downloader.run()
    finally:
        counters = metrics.run_counters
        summary = ", ".join(f"{count} {name}" for name, count in sorted(counters.items())) or "nothing to do"
        logger.info("Summary: %s; %d request(s), %d retry(ies)", summary, metrics.run_requests, metrics.run_retries)
        metrics.save(args, policy)
    if code:
        return code
    failed = counters["leagues failed"] + counters["match days failed"] + counters["pdfs failed"]
    return 1 if failed else 0


if __name__ == "__main__":
    from ingest_fcttweb.profile import BCNESA_PROFILE

    sys.exit(main(BCNESA_PROFILE))
