from __future__ import annotations

from ingest_bcnesa import download, parse
from ingest_bcnesa.ingestor import BcnesaIngestor
from ingest_common.run import IngestFilters, IngestRequest, IngestStage, NoOpListener
from ingest_common.season import Season
from ingest_common.settings import IngestSettings
from ingest_common.source import Source


def request(**filters):
    return IngestRequest(Source.BCNESA, Season.parse("2026-2027"), (IngestStage.DOWNLOAD, IngestStage.PARSE),
                         IngestFilters(**filters))


def test_territory_is_passed_to_the_downloader_only(tmp_path, monkeypatch):
    calls = {}
    monkeypatch.setattr(download, "main", lambda argv: calls.setdefault("download", argv) and 0)
    monkeypatch.setattr(parse, "main", lambda argv: calls.setdefault("parse", argv) and 0)
    ingestor, settings = BcnesaIngestor(), IngestSettings(tmp_path)
    filters = {"territory": "Barcelona", "match_days": frozenset({5})}

    assert not ingestor.download(request(**filters), settings, NoOpListener()).failed
    assert not ingestor.parse(request(**filters), settings, NoOpListener()).failed

    downloaded = calls["download"]
    assert downloaded[downloaded.index("--territory") + 1] == "Barcelona"
    assert downloaded[downloaded.index("--match_day") + 1] == "5"
    assert "--territory" not in calls["parse"]


def test_territory_is_a_supported_filter():
    assert "territory" in BcnesaIngestor().supported_filters


def test_download_writes_the_match_day_status_file(tmp_path, monkeypatch):
    import json

    from ingest_common.match_day_status import STATUS_FILE
    monkeypatch.setattr(download, "main", lambda argv: 1)  # even a download with failures refreshes the status
    report = BcnesaIngestor().download(request(), IngestSettings(tmp_path), NoOpListener())
    status_file = tmp_path / "bcnesa" / "content" / STATUS_FILE
    assert json.loads(status_file.read_text(encoding="utf-8"))["source"] == "BCNESA"
    assert report.issues  # exit code 1 still reported
