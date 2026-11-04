from __future__ import annotations

from datetime import datetime
from pathlib import Path, PurePosixPath

import pytest

from ingest_common.match_day_status import build_report
from ingest_common.run import IngestFilters, IngestRequest, IngestStage, NoOpListener
from ingest_common.scopes import scope_from_values
from ingest_common.season import Season
from ingest_common.settings import IngestSettings
from ingest_common.source import Source
from ingest_fctt import download, parse, status
from ingest_fctt.ingestor import FcttIngestor

CONTENT = Path(__file__).resolve().parent / "fixtures" / "content"
ACTA = "tdm/g1/regular/jornada_1_local_team_10_away_team_20.json"


def scoped(*scopes, delay=0):
    return IngestRequest(Source.FCTT, Season.parse("2026-2027"), (IngestStage.DOWNLOAD, IngestStage.PARSE),
                         delay_seconds=delay, scopes=tuple(IngestFilters(**scope) for scope in scopes))


def option(argv, name):
    return argv[argv.index(name) + 1] if name in argv else None


def test_each_scope_runs_the_scripts_once(tmp_path, monkeypatch):
    calls = {"download": [], "parse": []}
    monkeypatch.setattr(download, "main", lambda argv: calls["download"].append(argv) or 0)
    monkeypatch.setattr(parse, "main", lambda argv: calls["parse"].append(argv) or 0)
    ingestor, settings = FcttIngestor(), IngestSettings(tmp_path)
    req = scoped({"category": "tdm", "group": "g1", "match_days": frozenset({3, 4})},
                 {"gender": "female", "territory": "catalunya"})

    assert not ingestor.download(req, settings, NoOpListener()).failed
    assert not ingestor.parse(req, settings, NoOpListener()).failed

    assert [(option(argv, "--category"), option(argv, "--group"), option(argv, "--match_day"),
             option(argv, "--territory"), option(argv, "--request_delay")) for argv in calls["download"]] == [
        ("tdm", "g1", "3,4", None, "0"), ("female", None, None, "catalunya", "0")]
    assert [option(argv, "--category") for argv in calls["parse"]] == ["tdm", "female"]


def test_downloads_wait_the_script_delay_between_scopes(tmp_path, monkeypatch):
    import ingest_common.scan as scan

    pauses = []
    monkeypatch.setattr(scan.time, "sleep", pauses.append)
    monkeypatch.setattr(download, "main", lambda argv: 0)
    request = IngestRequest(Source.FCTT, Season.parse("2026-2027"), (IngestStage.DOWNLOAD,),
                            scopes=(IngestFilters(group="g1"), IngestFilters(group="g2")))
    FcttIngestor().download(request, IngestSettings(tmp_path), NoOpListener())
    assert pauses == [3.0]


@pytest.mark.parametrize("scope, day, expected", [
    ({"category": "tdm"}, 1, True),
    ({"category": "TDM", "group": "G1", "phase": "Regular"}, 1, True),
    ({"category": "copa-catalana-femenina"}, 1, False),
    ({"gender": "male"}, 1, True),
    ({"gender": "female"}, 1, False),
    ({"group": "g2"}, 1, False),
    ({"phase": "playoff"}, 1, False),
    ({"match_days": frozenset({1})}, 1, True),
    ({"match_days": frozenset({2})}, 1, False),
    ({"territory": "catalunya"}, 1, True),
])
def test_scope_matches_follows_the_actas_layout(scope, day, expected):
    assert FcttIngestor().scope_matches(IngestFilters(**scope), PurePosixPath(ACTA), day) is expected


def test_female_gender_selects_the_femenina_categories():
    acta = PurePosixPath("copa-catalana-femenina/divisio-1/regular/jornada_2_local_team_1_away_team_2.json")
    assert FcttIngestor().scope_matches(IngestFilters(gender="female"), acta, 2)
    assert not FcttIngestor().scope_matches(IngestFilters(gender="female"), PurePosixPath("tdm"), 2)


def test_status_row_is_a_valid_scope_for_parse_and_package():
    row = build_report(Source.FCTT, status.scan(CONTENT), datetime(2026, 10, 4, 12, 0))["matchDays"][0]
    scope = scope_from_values(category=row["category"], group=row["group"], phase=row["phase"],
                              gender=row["gender"], territory=row["territory"], match_days=[row["matchDay"]])
    pages, _ = parse.discover_pages(
        CONTENT, seasons={row["season"]}, categories=download.parse_filter(scope.category),
        groups=download.parse_filter(scope.group), phases=download.parse_filter(scope.phase),
        match_days=set(scope.match_days))
    assert [page.path.relative_to(CONTENT).as_posix() for page in pages] == [row["file"]]
    assert FcttIngestor().scope_matches(scope, PurePosixPath(ACTA), row["matchDay"])
