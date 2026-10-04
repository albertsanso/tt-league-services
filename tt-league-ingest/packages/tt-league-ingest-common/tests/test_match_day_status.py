from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone

import pytest

from ingest_common.match_day_status import (STATUS_FILE, MatchDayEntry, MatchState, ScanResult, build_report,
                                            classify, write_status_report)
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


def entry(day, matches, **fields):
    return MatchDayEntry(season="2026-2027", match_day=day, file=f"2026-2027/x/jornada-{day}.html",
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
