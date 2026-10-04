from __future__ import annotations

from pathlib import PurePosixPath

import pytest

from ingest_bcnesa import download, parse
from ingest_bcnesa.ingestor import BcnesaIngestor
from ingest_common.run import IngestFilters, IngestRequest, IngestStage, NoOpListener
from ingest_common.season import Season
from ingest_common.settings import IngestSettings
from ingest_common.source import Source


def request(**filters):
    return IngestRequest(Source.BCNESA, Season.parse("2026-2027"), (IngestStage.DOWNLOAD, IngestStage.PARSE),
                         IngestFilters(**filters))


def scoped(*scopes):
    return IngestRequest(Source.BCNESA, Season.parse("2026-2027"), (IngestStage.DOWNLOAD, IngestStage.PARSE),
                         delay_seconds=0, scopes=tuple(IngestFilters(**scope) for scope in scopes))


def option(argv, name):
    return argv[argv.index(name) + 1] if name in argv else None


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


def test_each_scope_downloads_once_and_scopes_differing_by_territory_parse_once(tmp_path, monkeypatch):
    calls = {"download": [], "parse": []}
    monkeypatch.setattr(download, "main", lambda argv: calls["download"].append(argv) or 0)
    monkeypatch.setattr(parse, "main", lambda argv: calls["parse"].append(argv) or 0)
    ingestor, settings = BcnesaIngestor(), IngestSettings(tmp_path)
    req = scoped({"territory": "Girona", "category": "PREFERENT", "group": "G1", "match_days": frozenset({3})},
                 {"territory": "Lleida", "category": "PREFERENT", "group": "G1", "match_days": frozenset({3})},
                 {"territory": "Barcelona", "category": "PRIMERA", "group": "G2", "phase": "1a Fase"})

    assert not ingestor.download(req, settings, NoOpListener()).failed
    assert not ingestor.parse(req, settings, NoOpListener()).failed

    assert [option(argv, "--territory") for argv in calls["download"]] == ["Girona", "Lleida", "Barcelona"]
    assert [option(argv, "--group") for argv in calls["download"]] == ["G1", "G1", "G2"]
    assert [option(argv, "--delay") for argv in calls["download"]] == ["0", "0", "0"]
    assert [(option(argv, "--category"), option(argv, "--phase"), option(argv, "--match_day"))
            for argv in calls["parse"]] == [("PREFERENT", None, "3"), ("PRIMERA", "1a Fase", None)]


def test_a_failing_scope_stops_the_download(tmp_path, monkeypatch):
    calls = []
    monkeypatch.setattr(download, "main", lambda argv: calls.append(argv) or 2)
    report = BcnesaIngestor().download(scoped({"group": "G1"}, {"group": "G2"}), IngestSettings(tmp_path),
                                       NoOpListener())
    assert report.failed and len(calls) == 1
    assert report.issues[0][0] == "bcnesa download [group=G1]"


@pytest.mark.parametrize("scope, relative, day, expected", [
    ({"category": "RTB PREFERENT"}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, True),
    ({"category": "preferent"}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, True),
    ({"category": "PRIMERA"}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, False),
    ({"group": "1"}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, True),
    ({"group": "G2"}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, False),
    ({"phase": "1A FASE"}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, True),
    ({"phase": "2a Fase"}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, False),
    ({"match_days": frozenset({3, 4})}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, True),
    ({"match_days": frozenset({4})}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, False),
    ({"territory": "Barcelona"}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, True),
    ({"territory": "Girona"}, "rtb-preferent/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, False),
    ({"territory": "Girona"}, "rtg-primera/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, True),
    ({"territory": "Girona"}, "veterans/G1/1a Fase/jornada_03_local_team_1_away_team_2.json", 3, True),
    ({"category": "RTB PREFERENT"}, "rtb-preferent/G1/jornada_03_local_team_1_away_team_2.json", 3, False),
])
def test_scope_matches_follows_the_actas_layout_and_parser_normalisation(scope, relative, day, expected):
    assert BcnesaIngestor().scope_matches(IngestFilters(**scope), PurePosixPath(relative), day) is expected
