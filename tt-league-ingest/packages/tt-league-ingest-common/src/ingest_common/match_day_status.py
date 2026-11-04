"""Match-day status report written to the download content root after every download.

The report is rebuilt from the saved pages on disk (never from run logs), so it always describes
the downloaded content. Each match day gets one status:

- ``complete``: every match has its report (acta) published.
- ``partial``: some matches are played, but not every match has its report yet.
- ``scheduled``: nothing played yet, but at least one match should already have started
  (its date has passed or is unknown): results are pending.
- ``future``: nothing played yet and every match starts after the report time.

Pages that list no matches are not match days; they are only counted (``emptyPages``).
"""

from __future__ import annotations

import json
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from ingest_common.json_io import write_json_atomically
from ingest_common.source import Source

STATUS_FILE = "match-days-status.json"
STATUSES = ("complete", "partial", "scheduled", "future")
REPORT_VERSION = 1


@dataclass(frozen=True)
class MatchState:
    played: bool
    reported: bool  # the match report (acta) is published and usable
    starts_at: datetime | None = None  # local wall time of the match, naive


@dataclass(frozen=True)
class MatchDayEntry:
    season: str
    match_day: int
    file: str  # path of the saved page, relative to the content root, ``/`` separated
    matches: tuple[MatchState, ...]
    content_updated_at: datetime
    category: str | None = None
    group: str | None = None
    phase: str | None = None
    gender: str | None = None
    territory: str | None = None


@dataclass
class ScanResult:
    entries: list[MatchDayEntry] = field(default_factory=list)
    empty_pages: int = 0
    issues: list[tuple[str, str]] = field(default_factory=list)  # (file, message) of unreadable pages


def naive_local(value: datetime | None) -> datetime | None:
    """Pages give local wall times; drop any timezone so every source compares the same way."""
    return value.replace(tzinfo=None) if value is not None and value.tzinfo is not None else value


def classify(matches: tuple[MatchState, ...] | list[MatchState], now: datetime) -> str:
    if not matches:
        raise ValueError("a match day needs at least one match")
    if all(match.reported for match in matches):
        return "complete"
    if any(match.played or match.reported for match in matches):
        return "partial"
    starts = [naive_local(match.starts_at) for match in matches]
    if all(start is not None and start > now for start in starts):
        return "future"
    return "scheduled"


def _iso(value: datetime | None) -> str | None:
    return value.isoformat(timespec="minutes") if value is not None else None


def build_report(source: Source, scan: ScanResult, now: datetime) -> dict[str, Any]:
    now = naive_local(now)
    rows = []
    totals: Counter = Counter()
    for entry in sorted(scan.entries, key=lambda e: (e.season, e.category or "", e.gender or "", e.group or "",
                                                     e.phase or "", e.match_day, e.file)):
        status = classify(entry.matches, now)
        starts = [naive_local(m.starts_at) for m in entry.matches if m.starts_at is not None]
        played = sum(m.played or m.reported for m in entry.matches)
        reported = sum(m.reported for m in entry.matches)
        totals[status] += 1
        totals["matches"] += len(entry.matches)
        totals["played"] += played
        totals["reported"] += reported
        rows.append({
            "season": entry.season,
            "category": entry.category,
            "group": entry.group,
            "phase": entry.phase,
            "gender": entry.gender,
            "territory": entry.territory,
            "matchDay": entry.match_day,
            "status": status,
            "matches": len(entry.matches),
            "played": played,
            "reported": reported,
            "firstMatchAt": _iso(min(starts)) if starts else None,
            "lastMatchAt": _iso(max(starts)) if starts else None,
            "file": entry.file,
            "contentUpdatedAt": entry.content_updated_at.astimezone(timezone.utc).isoformat(timespec="seconds"),
        })
    return {
        "version": REPORT_VERSION,
        "source": source.value,
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "evaluatedAt": _iso(now),
        "seasons": sorted({row["season"] for row in rows}),
        "summary": {
            "matchDays": len(rows),
            **{status: totals[status] for status in STATUSES},
            "matches": totals["matches"],
            "played": totals["played"],
            "reported": totals["reported"],
            "emptyPages": scan.empty_pages,
            "unreadablePages": len(scan.issues),
        },
        "matchDays": rows,
    }


def report_for_season(report: dict[str, Any], season: str) -> dict[str, Any] | None:
    """The report restricted to one season, or ``None`` when it has no match day of that season.

    The match-day counters of ``summary`` are recomputed from the kept rows; ``emptyPages`` and
    ``unreadablePages`` are not tracked per season and keep their whole-report values.
    """
    rows = [row for row in report.get("matchDays", []) if row.get("season") == season]
    if not rows:
        return None
    summary = dict(report.get("summary", {}))
    summary["matchDays"] = len(rows)
    for status in STATUSES:
        summary[status] = sum(row["status"] == status for row in rows)
    for name in ("matches", "played", "reported"):
        summary[name] = sum(row[name] for row in rows)
    return {**report, "seasons": [season], "summary": summary, "matchDays": rows}


def read_status_report(content_dir: Path) -> dict[str, Any] | None:
    """The current ``<content_dir>/match-days-status.json``, or ``None`` when no download has written one."""
    try:
        text = (content_dir / STATUS_FILE).read_text(encoding="utf-8")
    except FileNotFoundError:
        return None
    return json.loads(text)


def write_status_report(content_dir: Path, source: Source, scan: ScanResult, now: datetime | None = None) -> Path:
    """Write ``<content_dir>/match-days-status.json`` atomically and return its path."""
    path = content_dir / STATUS_FILE
    write_json_atomically(path, build_report(source, scan, now or datetime.now()))
    return path


def file_updated_at(path: Path) -> datetime:
    return datetime.fromtimestamp(path.stat().st_mtime, timezone.utc)


def relative(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()
