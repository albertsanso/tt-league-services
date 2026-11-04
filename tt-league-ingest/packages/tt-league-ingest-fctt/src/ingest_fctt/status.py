"""Scan the saved FCTT match-day pages into match-day status entries."""

from __future__ import annotations

import re
from pathlib import Path

from ingest_common.match_day_status import MatchDayEntry, MatchState, ScanResult, file_updated_at, relative
from ingest_fctt import download as dl

SEASON_RE = re.compile(r"\d{4}-\d{4}")
PAGE_RE = re.compile(r"jornada-(\d+)\.html")


def scan(content_dir: Path) -> ScanResult:
    """Pages are ``<season>/<category>/<group>/<phase>/jornada-<N>.html``."""
    result = ScanResult()
    if not content_dir.is_dir():
        return result
    for season_dir in sorted(p for p in content_dir.iterdir() if p.is_dir() and SEASON_RE.fullmatch(p.name)):
        for path in sorted(season_dir.rglob("jornada-*.html")):
            found = PAGE_RE.fullmatch(path.name)
            if not found or len(path.relative_to(season_dir).parts) != 4:
                continue
            day = int(found.group(1))
            try:
                text = path.read_text(encoding="utf-8", errors="replace")
                meta = dl.read_saved_meta(path)
                sections = dl.extract_sections(dl.parse_html(text))
            except (OSError, ValueError) as error:
                result.issues.append((relative(path, content_dir), str(error)))
                continue
            matches = sections.get(day) or sections.get(None) or []
            if not matches:
                result.empty_pages += 1
                continue
            category, group, phase = path.relative_to(season_dir).parts[:3]
            result.entries.append(MatchDayEntry(
                season=meta.get("acta-season") or season_dir.name,
                match_day=int(meta.get("acta-match-day") or day),
                file=relative(path, content_dir),
                # a walkover is played and complete without an acta; content_status covers it
                matches=tuple(MatchState(match.played, match.content_status == "complete", match.starts_at())
                              for match in matches),
                content_updated_at=file_updated_at(path),
                category=meta.get("acta-category") or category,
                group=meta.get("acta-group") or group,
                phase=meta.get("acta-phase") or phase,
                gender=meta.get("acta-gender") or None,
                territory=meta.get("acta-territory") or None,
            ))
    return result
