from __future__ import annotations

from datetime import datetime
from pathlib import Path

from ingest_common.match_day_status import build_report
from ingest_common.source import Source
from ingest_fctt import status

CONTENT = Path(__file__).resolve().parent / "fixtures" / "content"


def test_scan_real_fixture_page():
    report = build_report(Source.FCTT, status.scan(CONTENT), datetime(2026, 10, 4, 12, 0))
    assert report["summary"]["matchDays"] == 1 and report["summary"]["emptyPages"] == 0
    day = report["matchDays"][0]
    assert (day["season"], day["category"], day["group"], day["phase"], day["matchDay"]) == (
        "2026-2027", "tdm", "g1", "regular", 1)
    assert day["gender"] == "male" and day["status"] == "complete" and day["matches"] == day["reported"] == 6
    assert day["file"] == "2026-2027/tdm/g1/regular/jornada-1.html"


def test_same_page_before_kick_off_is_future_when_nothing_is_played(tmp_path):
    report = build_report(Source.FCTT, status.scan(tmp_path), datetime(2026, 10, 4))
    assert report["matchDays"] == [] and report["summary"]["matchDays"] == 0
