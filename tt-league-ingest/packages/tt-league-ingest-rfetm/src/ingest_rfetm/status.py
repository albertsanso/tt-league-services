"""Scan the saved RFETM jornada pages into match-day status entries."""

from __future__ import annotations

import re
from datetime import datetime
from pathlib import Path

from ingest_common.match_day_status import MatchDayEntry, MatchState, ScanResult, file_updated_at, relative
from ingest_rfetm.download import decode_html
from ingest_rfetm.parse import parse_html_matches

SEASON_RE = re.compile(r"\d{4}-\d{4}")
PAGE_RE = re.compile(r"grupo_(\d+)\.html")


def _starts_at(partido: dict) -> datetime | None:
    if not partido.get("fecha"):
        return None
    try:
        return datetime.fromisoformat(f"{partido['fecha']}T{partido.get('hora') or '00:00'}")
    except ValueError:
        return None


def scan(content_dir: Path) -> ScanResult:
    """Pages are ``<season>/<category>/<day>/<sex>/grupo_<N>.html`` (one match day of one group)."""
    result = ScanResult()
    if not content_dir.is_dir():
        return result
    for season_dir in sorted(p for p in content_dir.iterdir() if p.is_dir() and SEASON_RE.fullmatch(p.name)):
        for path in sorted(season_dir.rglob("grupo_*.html")):
            found = PAGE_RE.fullmatch(path.name)
            parts = path.relative_to(season_dir).parts
            if not found or len(parts) != 4 or not parts[1].isdigit():
                continue
            try:
                partidos = parse_html_matches(decode_html(path.read_bytes()))["partidos"]
            except (OSError, ValueError) as error:
                result.issues.append((relative(path, content_dir), str(error)))
                continue
            if not partidos:
                result.empty_pages += 1
                continue
            category, day, gender = parts[:3]
            result.entries.append(MatchDayEntry(
                season=season_dir.name,
                match_day=int(day),
                file=relative(path, content_dir),
                matches=tuple(MatchState(partido["marcador"] is not None, partido["acta_id"] is not None,
                                         _starts_at(partido))
                              for partido in partidos),
                content_updated_at=file_updated_at(path),
                category=category,
                group=found.group(1),
                gender=gender,
            ))
    return result
