"""Incremental parse of the downloaded match day pages into schema-validated JSON actas, one file per match.

Input is what the downloader saved (``<input_dir>/<season>/<category>/<group>/<phase>/<jornada page>``), output is
``<output_dir>/<season>/<category>/<group>/<phase>/jornada_<N>_local_team_<home id>_away_team_<away id>.json``
(folder values and file names follow the profile's layout). Each file follows ``docs/acta-model-definition.json``.

Two incremental policies exist (``SiteProfile.parse_policy``):

* ``published-guard`` (BCNESA): a JSON is only written when its content changes and a published acta is never
  replaced by an unpublished one (the site sometimes drops results temporarily) unless ``--force`` is given; a
  record that fails the schema is rejected.
* ``parse-state`` (FCTT): a state file skips the pages that did not change, JSON files a page no longer produces
  are removed and a record that fails the schema is still written but counted as invalid.

The exit code is 0 when everything was handled, 1 when something failed and 2 when the run could not start (bad
arguments, missing season directory or schema).
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import re
import sys
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

from bs4 import BeautifulSoup
from jsonschema import Draft202012Validator

from ingest_common import health, progress
from ingest_common.validation import ACTA_SCHEMA_PATH
from ingest_fcttweb import clock
from ingest_fcttweb.acta import (
    ActaLocation, PageContext, ParsedMatch, StatusActaBuilder, TableActaBuilder, GENDERS,
)
from ingest_fcttweb.files import atomic_write
from ingest_fcttweb.logging_setup import configure_script_logger
from ingest_fcttweb.markup import Reader, fold_compact, kebab_case, make_soup, normalise_group, slugify
from ingest_fcttweb.profile import SiteProfile

SCHEMA_PATH = ACTA_SCHEMA_PATH
SEASON_RE = re.compile(r"\d{4}-\d{4}")
STATE_FILE = ".parse_state.json"
# Bump when the parsing logic changes so that every page is parsed again.
PARSER_VERSION = 2  # 2: actas carry region / tipo_region


def current_season(today: Any = None) -> str:
    today = today or clock.local_now().date()
    start = today.year if today.month >= 8 else today.year - 1
    return f"{start}-{start + 1}"


def parse_match_days(value: str, *, strict: bool = False) -> set[int]:
    days: set[int] = set()
    for part in filter(None, (piece.strip() for piece in value.split(","))):
        bounds = re.fullmatch(r"(\d+)(?:\s*-\s*(\d+))?", part)
        if not bounds:
            raise argparse.ArgumentTypeError(f"invalid match day: {part!r} (use 3, 1,4 or 2-5)")
        first, last = int(bounds.group(1)), int(bounds.group(2) or bounds.group(1))
        if strict and (first < 1 or last < first):
            raise argparse.ArgumentTypeError(f"invalid match day range '{part}'")
        days.update(range(first if strict else min(first, last), (last if strict else max(first, last)) + 1))
    return days


def read_html(path: Path) -> str:
    """Read a saved page; the downloader writes UTF-8, older FCTT pages were Windows-1252."""
    raw = path.read_bytes()
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return raw.decode("cp1252", errors="replace")


def write_json(data: dict[str, Any], output_path: Path) -> None:
    atomic_write(output_path, (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))


def validation_errors(validator: Draft202012Validator, data: dict[str, Any]) -> list[str]:
    errors = sorted(validator.iter_errors(data), key=lambda error: list(error.absolute_path))
    return [f"{'/'.join(map(str, error.absolute_path)) or '(root)'}: {error.message}" for error in errors]


def read_meta(soup: BeautifulSoup, prefix: str = "") -> dict[str, str]:
    return {str(tag["name"]): str(tag.get("content", "")) for tag in soup.find_all("meta", attrs={"name": True})
            if str(tag["name"]).startswith(prefix)}


# =========================================================================== published-guard (BCNESA)


class GuardedParser:
    """Parses ``jornada_NN.html`` pages; a published JSON is never replaced by an unpublished one."""

    def __init__(self, profile: SiteProfile, args: argparse.Namespace, logger: logging.Logger):
        self.profile = profile
        self.layout = profile.layout
        self.meta = profile.layout.meta
        self.args = args
        self.logger = logger
        self.builder = TableActaBuilder(profile)

    # ---- discovery

    @staticmethod
    def category_matches(folder: str, wanted: str | None) -> bool:
        if not wanted:
            return True
        short = re.sub(r"^rt[bglt]", "", fold_compact(folder))
        return fold_compact(wanted) in {fold_compact(folder), short}

    @staticmethod
    def day_from_name(path: Path) -> int | None:
        match = re.search(r"(\d+)$", path.stem)
        return int(match.group(1)) if match else None

    def find_files(self, input_dir: Path) -> list[Path]:
        """Jornada HTML files at ``<season>/<category>/<group>/<phase>/<page>.html`` matching the filters."""
        args = self.args
        season_dir = input_dir / args.season
        if not season_dir.is_dir():
            raise ValueError(f"season directory not found: {season_dir}")
        files = []
        for path in season_dir.glob("*/*/*/*.html"):
            category, group, phase, _ = path.relative_to(season_dir).parts
            if not self.category_matches(category, args.category):
                continue
            if args.group and normalise_group(group) != normalise_group(args.group):
                continue
            if args.phase and fold_compact(phase) != fold_compact(args.phase):
                continue
            if args.match_day and self.day_from_name(path) not in args.match_day:
                continue
            files.append(path)
        return sorted(files)

    # ---- parsing

    def parse_jornada(self, text: str, relative_path: Path) -> tuple[str, list[dict[str, Any] | Exception]]:
        """Parse a jornada HTML; return its content status and one acta (or the error) per match."""
        soup = make_soup(text)
        meta = read_meta(soup)
        # The folders are authoritative: they are what the filters and output paths use.
        season, category, group, phase, _ = relative_path.parts
        day_value = meta.get(self.meta.match_day) or str(self.day_from_name(relative_path) or "")
        if not day_value.isdigit() or int(day_value) < 1:
            raise ValueError("cannot determine the match day")
        status = meta.get(self.meta.status) or "partial"
        if status not in ("partial", "complete"):
            self.logger.warning("%s: unknown %s %r, treated as partial", relative_path, self.meta.status, status)
            status = "partial"
        containers = soup.select(".match-container")
        if not containers:
            raise ValueError("no match found in the HTML")
        results: list[dict[str, Any] | Exception] = []
        for position, container in enumerate(containers, start=1):
            try:
                location = ActaLocation(season, category, group, phase,
                                        self.builder.stem(container, position, int(day_value)))
                results.append(self.builder.build(container, location, category, int(day_value)))
            except Exception as exc:  # Keep the other matches of the jornada.
                results.append(exc)
        return status, results

    # ---- output

    def output_dir_for(self, output_dir: Path, relative_path: Path) -> Path:
        season, category, group, phase, _ = relative_path.parts
        return output_dir / season / kebab_case(category) / group / phase

    def acta_filename(self, acta: dict[str, Any], position: int) -> str:
        """``jornada_02_local_team_151_away_team_247.json``, from the team ids of the acta."""
        home, away = (acta["equipos"][side].get("id") or f"m{position}" for side in ("local", "visitante"))
        return self.layout.json_name.format(day=acta["jornada"], home=home, away=away)

    @staticmethod
    def read_json(path: Path) -> dict[str, Any] | None:
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return None

    def write_acta(self, acta: dict[str, Any], output_path: Path, force: bool) -> str:
        """Write one acta unless nothing changed; return new, updated, unchanged or kept."""
        saved = self.read_json(output_path) if output_path.exists() else None
        if saved is not None and not force:
            if saved == acta:
                return "unchanged"
            if saved.get("acta_publicada", True) and not acta["acta_publicada"]:
                self.logger.warning("%s: keeping the published acta; the page no longer shows its results", output_path)
                return "kept"
        write_json(acta, output_path)
        return "new" if saved is None else "updated"

    def process_file(self, html_path: Path, input_dir: Path, output_dir: Path, validator: Draft202012Validator,
                     stats: Counter) -> None:
        relative_path = html_path.relative_to(input_dir)
        status, results = self.parse_jornada(read_html(html_path), relative_path)
        target_dir = self.output_dir_for(output_dir, relative_path)
        outcomes: Counter = Counter()
        for position, result in enumerate(results, start=1):
            try:
                if isinstance(result, Exception):
                    raise result
                errors = validation_errors(validator, result)
                if errors:
                    raise ValueError("schema validation failed: " + "; ".join(errors[:3]))
                if status == "complete" and not result["acta_publicada"]:
                    self.logger.warning("%s: %s is in a complete jornada but has no results table",
                                        relative_path, result["id_partido"])
                outcome = self.write_acta(result, target_dir / self.acta_filename(result, position), self.args.force)
            except Exception as exc:
                stats["matches failed"] += 1
                if not isinstance(exc, OSError):  # a write error is not a parse error
                    health.parse_error()
                self.logger.error("%s: match %d: %s", relative_path, position, exc)
                continue
            outcomes[outcome] += 1
            stats[f"matches {outcome}"] += 1
            stats["matches published" if result["acta_publicada"] else "matches not published"] += 1
        stats["jornadas parsed"] += 1
        detail = ", ".join(f"{count} {name}" for name, count in sorted(outcomes.items())) or "nothing written"
        self.logger.info("%s (%s) -> %s: %s", relative_path, status, target_dir.relative_to(output_dir), detail)

    def run(self) -> int:
        args = self.args
        try:
            validator = Draft202012Validator(json.loads(SCHEMA_PATH.read_text(encoding="utf-8")))
            files = self.find_files(args.input_dir)
        except (OSError, ValueError) as exc:
            self.logger.error("%s", exc)
            return 2
        if not files:
            self.logger.error("No jornada HTML matches the category/group/phase/match day filters")
            return 2
        self.logger.info("Parsing %d jornada file(s) for season %s (force=%s)", len(files), args.season, args.force)

        stats: Counter = Counter()
        progress.total(len(files))
        for html_path in files:
            try:
                self.process_file(html_path, args.input_dir, args.output_dir, validator, stats)
            except Exception as exc:  # Keep processing the remaining jornadas.
                stats["jornadas failed"] += 1
                if not isinstance(exc, OSError):
                    health.parse_error()
                self.logger.error("%s: %s", html_path.relative_to(args.input_dir), exc)
            progress.item(str(html_path.relative_to(args.input_dir)))
        summary = ", ".join(f"{count} {name}" for name, count in sorted(stats.items())) or "nothing to do"
        self.logger.info("Summary: %s", summary)
        return 1 if stats["jornadas failed"] or stats["matches failed"] else 0


def guarded_parser_args(profile: SiteProfile) -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--season", default=current_season(), help="Season, e.g. 2026-2027 (default: current season)")
    parser.add_argument("--category", help="Category, e.g. 'RTB PREFERENT', 'PREFERENT' or 'rtb-preferent' (default: all)")
    parser.add_argument("--group", help="Group, e.g. G1 or 1 (default: all)")
    parser.add_argument("--phase", help="Phase, e.g. '1a Fase' (default: all)")
    parser.add_argument("--match_day", type=parse_match_days, help="Match day(s): 3, 1,4 or 2-5 (default: all)")
    parser.add_argument("--input_dir", type=Path, required=True, help=f"Downloaded content (<data_dir>/{profile.name}/content)")
    parser.add_argument("--output_dir", type=Path, required=True, help=f"JSON output (<data_dir>/{profile.name}/actas-json)")
    parser.add_argument("--log_file", type=Path, required=True, help="Log file")
    parser.add_argument("--force", action="store_true", help="Rewrite every JSON, even unchanged or published ones")
    return parser


def main_guarded(profile: SiteProfile, argv: list[str] | None = None) -> int:
    parser = guarded_parser_args(profile)
    args = parser.parse_args(argv)
    if not re.fullmatch(r"\d{4}-\d{4}", args.season) or int(args.season[5:]) != int(args.season[:4]) + 1:
        parser.error("--season must look like 2026-2027")
    logger = configure_script_logger(profile.parse_logger, args.log_file)
    return GuardedParser(profile, args, logger).run()


# =========================================================================== parse-state (FCTT)


@dataclass
class MatchDayPage:
    """One saved page and its position in the content tree."""

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


@dataclass
class PageResult:
    matches: list[ParsedMatch] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


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
    schema: Path | None = SCHEMA_PATH


LEAGUE_SEASON_RE = re.compile(r"\s*-?\s*Temporada\s+\d{4}\s*[/-]\s*\d{4}.*$", re.I)


class SchemaValidator:
    def __init__(self, schema_path: Path | None, logger: logging.Logger):
        self.validator = None
        if schema_path is None:
            return
        try:
            schema = json.loads(schema_path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            logger.warning("Acta model %s not loaded (%s); JSON records are not validated", schema_path, error)
            return
        self.validator = Draft202012Validator(schema, format_checker=Draft202012Validator.FORMAT_CHECKER)

    def errors(self, record: dict[str, Any]) -> list[str]:
        if self.validator is None:
            return []
        return validation_errors(self.validator, record)


class CompetitionNames:
    """Competition name of each group folder, also for pages without league heading (calendar pages)."""

    def __init__(self, reader: Reader, html_glob: str) -> None:
        self.reader = reader
        self.html_glob = html_glob
        self._cache: dict[Path, str | None] = {}

    def league_title(self, soup: BeautifulSoup) -> str | None:
        """Competition name from the league heading, without the season ("Tercera Divisió Masculina Grup 1")."""
        node = soup.select_one(".br-apic-league-title")
        title = LEAGUE_SEASON_RE.sub("", self.reader.text(node)).strip(" -") if node else ""
        return title or None

    def fallback_title(self, soup: BeautifulSoup) -> str | None:
        """Page heading ("TDM G1") or ``<title>`` without the jornada, for pages without league heading."""
        node = soup.select_one(".entry-title")
        if node and self.reader.text(node):
            return self.reader.text(node)
        title = soup.find("title")
        text = re.sub(r"\s*[-|–].*$", "", self.reader.text(title)).strip() if title else ""
        return text or None

    def resolve(self, page: MatchDayPage, soup: BeautifulSoup) -> str | None:
        folder = page.path.parent
        if title := self.league_title(soup):
            self._cache.setdefault(folder, title)
            return title
        if folder not in self._cache:
            self._cache[folder] = None
            for sibling in sorted(folder.glob(self.html_glob)):
                if sibling == page.path:
                    continue
                try:
                    text = sibling.read_text(encoding="utf-8", errors="replace")
                except OSError:
                    continue
                if "br-apic-league-title" in text and (title := self.league_title(make_soup(text))):
                    self._cache[folder] = title
                    break
        return self._cache[folder] or self.fallback_title(soup)


class StatefulParser:
    """Parses ``jornada-<N>.html`` pages incrementally with a state file; removes JSON a page no longer produces."""

    def __init__(self, profile: SiteProfile, options: Options, logger: logging.Logger):
        self.profile = profile
        self.layout = profile.layout
        self.meta = profile.layout.meta
        self.options = options
        self.logger = logger
        self.reader = Reader(profile.text_mode)
        self.builder = StatusActaBuilder(profile, warn=logger.warning)
        self.validator = SchemaValidator(options.schema, logger)
        self.competitions = CompetitionNames(self.reader, profile.layout.html_glob)
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
            self.logger.warning("Parse state %s not readable (%s); every page is parsed again", self.state_path, error)
            return {"parser_version": PARSER_VERSION, "pages": {}}
        if state.get("parser_version") != PARSER_VERSION or not isinstance(state.get("pages"), dict):
            self.logger.info("Parser version changed; every page is parsed again")
            return {"parser_version": PARSER_VERSION, "pages": state.get("pages") or {}, "stale": True}
        return state

    def _save_state(self) -> None:
        if self.options.dry_run:
            return
        self.state.pop("stale", None)
        self.state["parser_version"] = PARSER_VERSION
        self.state["updated_at"] = clock.utc_iso()
        atomic_write(self.state_path, (json.dumps(self.state, ensure_ascii=False, indent=1, sort_keys=True) + "\n").encode("utf-8"))

    # ---- discovery

    def discover_pages(self) -> tuple[list[MatchDayPage], int]:
        """Match day pages under ``input_dir`` that pass the filters, and the number of PDF actas found."""
        options, log = self.options, self.logger
        regex = re.compile(self.layout.html_regex, re.I)
        pages: list[MatchDayPage] = []
        pdfs = 0
        for path in sorted(options.input_dir.rglob("*")):
            if not path.is_file():
                continue
            parts = path.relative_to(options.input_dir).parts
            if path.suffix.casefold() == ".pdf":
                pdfs += 1
                continue
            found = regex.fullmatch(path.name)
            if not found or len(parts) != 5 or not SEASON_RE.fullmatch(parts[0]):
                if path.suffix.casefold() == ".html":
                    log.debug("Ignored (not <season>/<category>/<group>/<phase>/<match day page>): %s", path)
                continue
            season, category, group, phase = parts[0], slugify(parts[1]), slugify(parts[2]), slugify(parts[3])
            if not (category and group and phase):
                log.warning("Ignored, empty category/group/phase folder name: %s", path)
                continue
            jornada = int(found.group(1))
            if ((options.seasons is not None and season not in options.seasons)
                    or (options.categories is not None and category not in options.categories)
                    or (options.groups is not None and group not in options.groups)
                    or (options.phases is not None and phase not in options.phases)
                    or (options.match_days is not None and jornada not in options.match_days)):
                continue
            pages.append(MatchDayPage(path, season, category, group, phase, jornada))
        pages.sort(key=lambda page: (page.season, page.category, page.group, page.phase, page.jornada))
        return pages, pdfs

    # ---- parsing

    def parse_page(self, page: MatchDayPage, text: str) -> PageResult:
        meta_keys = self.meta
        soup = make_soup(text)
        meta = read_meta(soup, "acta-")
        result = PageResult()
        page_status = meta.get(meta_keys.status, "").casefold() or None
        if page_status not in (None, "complete", "partial"):
            self.logger.warning("%s: unknown %s '%s'", page.key, meta_keys.status, page_status)
        for name, expected in ((meta_keys.season, page.season), (meta_keys.category, page.category),
                               (meta_keys.group, page.group), (meta_keys.phase, page.phase),
                               (meta_keys.match_day, str(page.jornada))):
            if meta.get(name) and slugify(meta[name]) != slugify(expected):
                self.logger.warning("%s: %s '%s' differs from the folder ('%s'); the folder is used",
                                    page.key, name, meta[name], expected)
        gender = meta.get(meta_keys.gender or "", "").casefold()
        if gender not in GENDERS:
            gender = "female" if "femen" in page.category else "male"
        context = PageContext(page.season, page.category, page.group, page.phase, page.jornada,
                              self.competitions.resolve(page, soup), gender)
        common = self.builder.common(context)
        containers = soup.find_all("div", class_="match-container")
        if not containers:
            message = "no matches found"
            if "No s'han trobat resultats" in text:
                message += " (\"No s'han trobat resultats\")"
            result.errors.append(message)
            return result
        expected = meta.get(meta_keys.matches or "", "")
        if expected.isdigit() and int(expected) != len(containers):
            self.logger.warning("%s: %s matches expected (%s), %s found", page.key, expected, meta_keys.matches,
                                len(containers))
        for number, container in enumerate(containers, 1):
            try:
                result.matches.append(self.builder.parse_match(container, context, common, page_status))
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

    def run(self) -> bool:
        options, log = self.options, self.logger
        pages, pdfs = self.discover_pages()
        self.counters["pdf_ignored"] = pdfs
        if pdfs:
            log.info("%s PDF acta(s) found and ignored (PDF parsing is not supported yet)", pdfs)
        if not pages:
            log.warning("No match day pages found in %s for the selected filters", options.input_dir)
            return True
        log.info("%s match day page(s) to check in %s", len(pages), options.input_dir)
        progress.total(len(pages))
        try:
            for page in pages:
                self.counters["pages"] += 1
                self._process(page)
                progress.item(str(page.relative))
        finally:
            self._save_state()
        return self.counters["pages_failed"] == 0 and self.counters["invalid"] == 0

    def _process(self, page: MatchDayPage) -> None:
        log = self.logger
        try:
            data = page.path.read_bytes()
        except OSError as error:
            log.error("%s: not readable: %s", page.key, error)
            self.counters["pages_failed"] += 1
            return
        digest = hashlib.sha256(data).hexdigest()
        previous = self.state["pages"].get(page.key, {})
        outputs = [self.options.output_dir / name for name in previous.get("outputs", [])]
        if (not self.options.force and not self.state.get("stale") and previous.get("sha256") == digest
                and outputs and all(path.exists() for path in outputs)):
            log.debug("%s: unchanged since the last run", page.key)
            self.counters["pages_unchanged"] += 1
            return

        try:
            result = self.parse_page(page, data.decode("utf-8", errors="replace"))
        except Exception as error:  # a broken page must not stop the run
            log.exception("%s: parsing failed: %s", page.key, error)
            health.parse_error()
            self.counters["pages_failed"] += 1
            return
        self.counters["pages_parsed"] += 1
        for message in result.errors:
            log.error("%s: %s", page.key, message)
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
                log.warning("%s: %s: %s", page.key, match.filename, warning)
                self.counters["warnings"] += 1
            if errors := self.validator.errors(match.record):
                self.counters["invalid"] += 1
                log.error("%s: %s does not follow the acta model: %s", page.key, match.filename, "; ".join(errors[:5]))
            self._write(self.options.output_dir / relative, match.record)

        published = sum(match.published for match in result.matches)
        log.info("%s: %s match(es), %s published, %s not published", page.key, len(result.matches),
                 published, len(result.matches) - published)
        if result.errors:
            # Keep previous outputs and parse the page again next run.
            return
        for stale in sorted(set(previous.get("outputs", [])) - set(written)):
            path = self.options.output_dir / stale
            if path.exists():
                log.info("%s: removing JSON no longer produced by the page: %s", page.key, path)
                if not self.options.dry_run:
                    path.unlink()
                self.counters["json_removed"] += 1
        self.state["pages"][page.key] = {
            "sha256": digest,
            "status": self.read_status(data),
            "outputs": written,
            "parsed_at": clock.utc_iso(),
        }

    def read_status(self, data: bytes) -> str | None:
        found = re.search(rb'<meta name="' + re.escape(self.meta.status.encode()) + rb'" content="([a-z]+)"', data)
        return found.group(1).decode() if found else None

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
        self.logger.debug("JSON %s: %s", "would be written" if self.options.dry_run else "written", path)


def parse_filter(value: str | None) -> set[str] | None:
    if not value or value.strip().casefold() == "all":
        return None
    return {slugify(part) for part in value.split(",") if part.strip()}


def latest_season(input_dir: Path) -> str | None:
    seasons = sorted(path.name for path in input_dir.iterdir() if path.is_dir() and SEASON_RE.fullmatch(path.name))
    return seasons[-1] if seasons else None


def stateful_parser_args(profile: SiteProfile) -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--season", help="Season YYYY-YYYY, or 'all' (default: latest season in --input_dir)")
    parser.add_argument("--category", help="Category slug(s), comma separated, e.g. tdm, copa-catalana-femenina (default: all)")
    parser.add_argument("--group", help="Group slug(s), comma separated, e.g. g1, 1a-divisio (default: all)")
    parser.add_argument("--phase", help="Phase slug(s), comma separated, e.g. regular (default: all)")
    parser.add_argument("--match_day", type=lambda value: parse_match_days(value, strict=True),
                        help="Match days, e.g. 3, 1-5 or 1,4,7 (default: all)")
    parser.add_argument("--input_dir", type=Path, required=True)
    parser.add_argument("--output_dir", type=Path, required=True)
    parser.add_argument("--log_file", type=Path, required=True)
    parser.add_argument("--schema", type=Path, default=SCHEMA_PATH, help="Acta model used to validate the JSON files")
    parser.add_argument("--no_validate", action="store_true", help="Do not validate the JSON files against the acta model")
    parser.add_argument("--force", action="store_true", help="Parse every page even if it has not changed")
    parser.add_argument("--dry_run", action="store_true", help="Parse and validate without writing any file")
    parser.add_argument("--verbose", action="store_true", help="Debug output")
    return parser


def main_stateful(profile: SiteProfile, argv: list[str] | None = None) -> int:
    parser = stateful_parser_args(profile)
    args = parser.parse_args(argv)
    if args.season and args.season.casefold() != "all" and not SEASON_RE.fullmatch(args.season):
        parser.error("--season must look like 2026-2027 (or 'all')")
    logger = configure_script_logger(profile.parse_logger, args.log_file,
                                     logging.DEBUG if args.verbose else logging.INFO)
    if not args.input_dir.is_dir():
        logger.error("Input directory not found: %s", args.input_dir)
        return 2
    if args.season and args.season.casefold() == "all":
        seasons = None
    else:
        season = args.season or latest_season(args.input_dir)
        if not season:
            logger.error("No season folder (YYYY-YYYY) in %s", args.input_dir)
            return 2
        seasons = {season}
        logger.info("Season %s", season)

    options = Options(
        input_dir=args.input_dir, output_dir=args.output_dir, seasons=seasons,
        categories=parse_filter(args.category), groups=parse_filter(args.group), phases=parse_filter(args.phase),
        match_days=args.match_day, force=args.force, dry_run=args.dry_run,
        schema=None if args.no_validate else args.schema,
    )
    runner = StatefulParser(profile, options, logger)
    started = datetime.now()
    ok = False
    try:
        ok = runner.run()
    except KeyboardInterrupt:
        logger.warning("Interrupted; parse state saved")
    counters = runner.counters
    logger.info(
        "Done in %.1f s%s: %s page(s) parsed, %s unchanged, %s failed; %s match(es) (%s published, %s not published); "
        "JSON %s written, %s unchanged, %s removed; %s invalid, %s warning(s), %s PDF ignored",
        (datetime.now() - started).total_seconds(), " (dry run)" if args.dry_run else "",
        counters["pages_parsed"], counters["pages_unchanged"], counters["pages_failed"], counters["matches"],
        counters["published"], counters["not_published"], counters["json_written"], counters["json_unchanged"],
        counters["json_removed"], counters["invalid"], counters["warnings"], counters["pdf_ignored"])
    return 0 if ok else 1


MODELS = {"published-guard": main_guarded, "parse-state": main_stateful}


def main(profile: SiteProfile, argv: list[str] | None = None) -> int:
    """Run the parser of ``profile`` with script-style arguments; returns the script exit code."""
    return MODELS[profile.parse_policy](profile, argv)


if __name__ == "__main__":
    from ingest_fcttweb.profile import FCTT_PROFILE

    sys.exit(main(FCTT_PROFILE, sys.argv[1:]))
