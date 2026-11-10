"""Scan the saved match day pages into match-day status entries (``ingest_common.match_day_status``)."""

from __future__ import annotations

import re
from pathlib import Path

from ingest_common.match_day_status import MatchDayEntry, MatchState, ScanResult, file_updated_at, relative
from ingest_common.region import region_values
from ingest_fcttweb import calendar_checked, validated
from ingest_fcttweb.markup import Reader, make_soup
from ingest_fcttweb.profile import SiteProfile

SEASON_RE = re.compile(r"\d{4}-\d{4}")


def scan(profile: SiteProfile, content_dir: Path) -> ScanResult:
    """Pages are ``<season>/<category>/<group>/<phase>/<match day page>`` (the profile's file name)."""
    result = ScanResult()
    if not content_dir.is_dir():
        return result
    layout, keys = profile.layout, profile.layout.meta
    page_re = re.compile(layout.html_regex)
    reader = Reader(profile.text_mode)
    for season_dir in sorted(p for p in content_dir.iterdir() if p.is_dir() and SEASON_RE.fullmatch(p.name)):
        for path in sorted(season_dir.rglob(layout.html_glob)):
            found = page_re.fullmatch(path.name)
            if not found or len(path.relative_to(season_dir).parts) != 4:
                continue
            day = int(found.group(1))
            try:
                if profile.status_model == "table-present":
                    soup, meta = validated.read_saved(path)
                    if soup is None:
                        result.issues.append((relative(path, content_dir), "empty file"))
                        continue
                    containers = soup.select(".match-container")
                    matches = validated.parse_matches(reader, containers, meta.get(keys.source_url, ""))
                    states = tuple(MatchState(match.played, bool(match.played and match.acta_url),
                                              validated.match_datetime(reader, container))
                                   for match, container in zip(matches, containers))
                else:
                    text = path.read_text(encoding="utf-8", errors="replace")
                    meta = calendar_checked.read_saved_meta(path)
                    sections = calendar_checked.extract_sections(reader, make_soup(text))
                    day_matches = sections.get(day) or sections.get(None) or []
                    # a walkover is played and complete without an acta; content_status covers it
                    states = tuple(MatchState(match.played, match.content_status == "complete", match.starts_at())
                                   for match in day_matches)
            except (OSError, ValueError) as error:
                result.issues.append((relative(path, content_dir), str(error)))
                continue
            if not states:
                result.empty_pages += 1
                continue
            category, group, phase = path.relative_to(season_dir).parts[:3]
            category_value = meta.get(keys.category) or category
            region = region_values(profile.source, category_value)
            result.entries.append(MatchDayEntry(
                season=meta.get(keys.season) or season_dir.name,
                match_day=int(meta.get(keys.match_day) or day),
                file=relative(path, content_dir),
                matches=states,
                content_updated_at=file_updated_at(path),
                category=category_value,
                group=meta.get(keys.group) or group,
                phase=meta.get(keys.phase) or phase,
                gender=(meta.get(keys.gender) or None) if keys.gender else None,
                territory=meta.get(keys.territory) or None,
                region=region[0] if region else None,
                region_type=region[1] if region else None,
            ))
    return result
