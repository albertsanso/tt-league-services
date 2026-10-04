"""Scan the saved BCNESA jornada pages into match-day status entries."""

from __future__ import annotations

import re
from pathlib import Path

from ingest_bcnesa import download as dl
from ingest_common.match_day_status import MatchDayEntry, MatchState, ScanResult, file_updated_at, relative

SEASON_RE = re.compile(r"\d{4}-\d{4}")
PAGE_RE = re.compile(r"jornada_(\d+)\.html")


def scan(content_dir: Path) -> ScanResult:
    """Pages are ``<season>/<category>/<group>/<phase>/jornada_NN.html``."""
    result = ScanResult()
    if not content_dir.is_dir():
        return result
    for season_dir in sorted(p for p in content_dir.iterdir() if p.is_dir() and SEASON_RE.fullmatch(p.name)):
        for path in sorted(season_dir.rglob("jornada_*.html")):
            found = PAGE_RE.fullmatch(path.name)
            if not found or len(path.relative_to(season_dir).parts) != 4:
                continue
            try:
                soup, meta = dl.read_saved(path)
            except (OSError, ValueError) as error:
                result.issues.append((relative(path, content_dir), str(error)))
                continue
            if soup is None:
                result.issues.append((relative(path, content_dir), "empty file"))
                continue
            containers = soup.select(".match-container")
            if not containers:
                result.empty_pages += 1
                continue
            matches = dl.parse_matches(containers, meta.get("source-url", ""))
            category, group, phase = path.relative_to(season_dir).parts[:3]
            result.entries.append(MatchDayEntry(
                season=meta.get("season") or season_dir.name,
                match_day=int(meta.get("match-day") or found.group(1)),
                file=relative(path, content_dir),
                matches=tuple(MatchState(match.played, bool(match.played and match.acta_url),
                                         dl.match_datetime(container))
                              for match, container in zip(matches, containers)),
                content_updated_at=file_updated_at(path),
                category=meta.get("category") or category,
                group=meta.get("group") or group,
                phase=meta.get("phase") or phase,
                territory=meta.get("territory") or None,
            ))
    return result
