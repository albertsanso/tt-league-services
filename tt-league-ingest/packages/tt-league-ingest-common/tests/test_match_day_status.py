from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone

import pytest

from ingest_common.match_day_status import (STATUS_FILE, MatchDayEntry, MatchState, ScanResult, build_report,
                                            classify, read_status_report, report_for_season, write_status_report)
from ingest_common.source import Source

NOW = datetime(2026, 10, 4, 12, 0)
PAST, LATER = NOW - timedelta(days=1), NOW + timedelta(days=6)
DONE = MatchState(played=True, reported=True, starts_at=PAST)
PLAYED_NO_ACTA = MatchState(played=True, reported=False, starts_at=PAST)
DUE = MatchState(played=False, reported=False, starts_at=PAST)
UPCOMING = MatchState(played=False, reported=False, starts_at=LATER)
UNDATED = MatchState(played=False, reported=False, starts_at=None)


@pytest.mark.parametrize("matches,expected", [
    ((DONE, DONE), "complete"),
    ((DONE, UPCOMING), "partial"),
    ((PLAYED_NO_ACTA, DONE), "partial"),
    ((DUE, UPCOMING), "scheduled"),
    ((UNDATED,), "scheduled"),
    ((UPCOMING, UPCOMING), "future"),
])
def test_classify(matches, expected):
    assert classify(matches, NOW) == expected


def test_classify_compares_aware_times_as_local_wall_time():
    aware = MatchState(False, False, datetime(2026, 10, 4, 13, 0, tzinfo=timezone(timedelta(hours=2))))
    assert classify((aware,), NOW) == "future"


def test_classify_rejects_empty_match_day():
    with pytest.raises(ValueError):
        classify((), NOW)


def entry(day, matches, season="2026-2027", **fields):
    return MatchDayEntry(season=season, match_day=day, file=f"{season}/x/jornada-{day}.html",
                         matches=tuple(matches), content_updated_at=datetime(2026, 10, 4, 9, 0, tzinfo=timezone.utc),
                         **fields)


def test_report_shape_summary_and_ordering(tmp_path):
    scan = ScanResult([entry(2, [UPCOMING]), entry(1, [DONE, PLAYED_NO_ACTA], category="tdm", group="g1")],
                      empty_pages=3, issues=[("bad.html", "boom")])
    path = write_status_report(tmp_path, Source.FCTT, scan, NOW)
    assert path == tmp_path / STATUS_FILE
    report = json.loads(path.read_text(encoding="utf-8"))
    assert report["source"] == "FCTT" and report["seasons"] == ["2026-2027"] and report["version"] == 1
    assert report["evaluatedAt"] == "2026-10-04T12:00"
    assert report["summary"] == {"matchDays": 2, "complete": 0, "partial": 1, "scheduled": 0, "future": 1,
                                 "matches": 3, "played": 2, "reported": 1, "emptyPages": 3, "unreadablePages": 1}
    first = report["matchDays"][0]
    assert [day["matchDay"] for day in report["matchDays"]] == [2, 1]  # sorted by category first ("" < "tdm")
    second = report["matchDays"][1]
    assert second == {
        "season": "2026-2027", "category": "tdm", "group": "g1", "phase": None, "gender": None, "territory": None,
        "matchDay": 1, "status": "partial", "matches": 2, "played": 2, "reported": 1,
        "firstMatchAt": "2026-10-03T12:00", "lastMatchAt": "2026-10-03T12:00",
        "file": "2026-2027/x/jornada-1.html", "contentUpdatedAt": "2026-10-04T09:00:00+00:00"}
    assert first["status"] == "future"


def test_report_is_rewritten_from_the_current_scan(tmp_path):
    write_status_report(tmp_path, Source.BCNESA, ScanResult([entry(1, [UPCOMING])]), NOW)
    write_status_report(tmp_path, Source.BCNESA, ScanResult([entry(1, [DONE])]), NOW)
    report = json.loads((tmp_path / STATUS_FILE).read_text(encoding="utf-8"))
    assert [day["status"] for day in report["matchDays"]] == ["complete"]
    assert not list(tmp_path.glob(".*.tmp"))


def test_empty_content_gives_an_empty_report(tmp_path):
    report = build_report(Source.RFETM, ScanResult(), NOW)
    assert report["matchDays"] == [] and report["seasons"] == [] and report["summary"]["matchDays"] == 0


def test_report_for_season_keeps_its_rows_and_recomputes_the_summary():
    scan = ScanResult([entry(1, [DONE, DONE]), entry(2, [UPCOMING]), entry(9, [DUE], season="2025-2026")],
                      empty_pages=2, issues=[("bad.html", "boom")])
    report = build_report(Source.FCTT, scan, NOW)
    current = report_for_season(report, "2026-2027")
    assert current["seasons"] == ["2026-2027"] and current["source"] == "FCTT"
    assert [day["matchDay"] for day in current["matchDays"]] == [1, 2]
    assert current["summary"] == {"matchDays": 2, "complete": 1, "partial": 0, "scheduled": 0, "future": 1,
                                  "matches": 3, "played": 2, "reported": 2, "emptyPages": 2, "unreadablePages": 1}
    previous = report_for_season(report, "2025-2026")
    assert previous["summary"]["scheduled"] == 1 and previous["summary"]["matchDays"] == 1
    assert report_for_season(report, "2027-2028") is None
    assert report["summary"]["matchDays"] == 3  # the full report is left untouched


def test_read_status_report(tmp_path):
    assert read_status_report(tmp_path) is None
    write_status_report(tmp_path, Source.RFETM, ScanResult([entry(1, [DONE])]), NOW)
    assert read_status_report(tmp_path)["matchDays"][0]["status"] == "complete"
