from __future__ import annotations

import json

import pytest

from ingest_cli.main import (EXIT_ISSUES, EXIT_NO_CHANGES, EXIT_OK, EXIT_SOURCE_UNAVAILABLE, EXIT_USAGE,
                             build_parser, build_request, main)
from ingest_common.run import IngestStage, StageReport
from ingest_common.scan import record_exit_code
from ingest_common.source import Source


class FakeIngestor:
    source = Source.FCTT
    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE})
    supported_filters = frozenset({"category", "group", "phase", "match_days", "gender", "territory"})
    invalid = 0
    download_exit = 0
    write_page = False
    write_acta = False

    def __init__(self):
        self.requests = []

    def download(self, request, settings, listener):
        self.requests.append(request)
        report = StageReport(IngestStage.DOWNLOAD)
        if self.write_page:
            page = settings.content_dir(request.source) / str(request.season) / "jornada_01.html"
            page.parent.mkdir(parents=True, exist_ok=True)
            page.write_text("<html/>", encoding="utf-8")
        record_exit_code(report, "fake download", self.download_exit)
        return report

    def parse(self, request, settings, listener):
        self.requests.append(request)
        report = StageReport(IngestStage.PARSE)
        report.count("invalid", self.invalid)
        if self.write_acta:
            acta = settings.actas_json_dir(request.source) / str(request.season) / "g1" / "acta.json"
            acta.parent.mkdir(parents=True, exist_ok=True)
            acta.write_text(json.dumps({"jornada": 1, "acta_publicada": True}), encoding="utf-8")
        return report


class Silent:
    def stage_started(self, stage):
        pass

    def item_processed(self, stage, item):
        pass

    def stage_finished(self, report):
        pass


def run(tmp_path, *args, ingestor=None):
    ingestor = ingestor or FakeIngestor()
    code = main([*args, "--data-dir", str(tmp_path)], ingestors={Source.FCTT: ingestor}, listener=Silent())
    return code, ingestor


def test_run_delegates_download_then_parse_with_filters(tmp_path):
    code, ingestor = run(tmp_path, "run", "--source", "fctt", "--season", "2026-2027", "--match-day", "2-3",
                         "--group", "g1", "--force")
    assert code == EXIT_OK
    assert [r.stages for r in ingestor.requests] == [(IngestStage.DOWNLOAD, IngestStage.PARSE)] * 2
    request = ingestor.requests[0]
    assert request.filters.match_days == frozenset({2, 3}) and request.filters.group == "g1" and request.force


def test_territory_filter_reaches_the_request(tmp_path):
    code, ingestor = run(tmp_path, "download", "--source", "fctt", "--territory", "Barcelona")
    assert code == EXIT_OK and ingestor.requests[0].filters.territory == "Barcelona"


def test_run_stage_selection():
    parser = build_parser()
    stages = lambda *a: build_request(parser.parse_args(["run", "--source", "fctt", *a])).stages  # noqa: E731
    assert stages() == (IngestStage.DOWNLOAD, IngestStage.PARSE)
    assert stages("--package")[-1] is IngestStage.PACKAGE
    assert stages("--upload")[-2:] == (IngestStage.PACKAGE, IngestStage.UPLOAD)


def test_invalid_arguments_exit_with_usage_code(tmp_path, capsys):
    assert run(tmp_path, "run", "--source", "fctt", "--season", "2026-2028")[0] == EXIT_USAGE
    assert run(tmp_path, "run", "--source", "nope")[0] == EXIT_USAGE
    assert run(tmp_path, "run", "--source", "fctt", "--match-day", "0")[0] == EXIT_USAGE
    assert run(tmp_path, "package", "--source", "fctt", "--mode", "delta")[0] == EXIT_USAGE
    assert run(tmp_path, "download")[0] == EXIT_USAGE


def test_missing_data_dir_is_a_usage_error(monkeypatch, capsys):
    monkeypatch.delenv("TT_INGEST_DATA_DIR", raising=False)
    assert main(["download", "--source", "fctt"], ingestors={}, listener=Silent()) == EXIT_USAGE
    assert "TT_INGEST_DATA_DIR" in capsys.readouterr().err


def test_unsupported_filter_for_source_fails_without_calls(tmp_path):
    class NoGroups(FakeIngestor):
        supported_filters = frozenset({"category"})

    code, ingestor = run(tmp_path, "download", "--source", "fctt", "--group", "g1", ingestor=NoGroups())
    assert code == EXIT_ISSUES and ingestor.requests == []
    code, ingestor = run(tmp_path, "download", "--source", "fctt", "--territory", "Barcelona", ingestor=NoGroups())
    assert code == EXIT_ISSUES and ingestor.requests == []


def test_issues_map_to_exit_code_one_and_json_shape(tmp_path, capsys):
    ingestor = FakeIngestor()
    ingestor.invalid = 2
    code, _ = run(tmp_path, "parse", "--source", "fctt", "--season", "2026-2027", "--json", ingestor=ingestor)
    payload = json.loads(capsys.readouterr().out)
    assert code == EXIT_ISSUES
    assert payload["status"] == "COMPLETED_WITH_ISSUES" and payload["source"] == "FCTT"
    assert payload["stages"][0]["counters"]["invalid"] == 2
    assert set(payload) >= {"season", "startedAt", "finishedAt", "stages", "outputs"}
    assert payload["outcome"] == "COMPLETED_WITH_ISSUES" and payload["retryable"] is False
    assert payload["changes"] == {"contentChanged": 0, "actasChanged": 0}


def test_run_that_changes_nothing_exits_three_and_skips_package(tmp_path, capsys):
    code, ingestor = run(tmp_path, "run", "--source", "fctt", "--season", "2026-2027", "--package", "--json")
    payload = json.loads(capsys.readouterr().out)
    assert code == EXIT_NO_CHANGES == 3
    assert payload["outcome"] == "NO_CHANGES" and payload["status"] == "SUCCEEDED" and payload["retryable"] is False
    assert payload["stages"][-1]["stage"] == "PACKAGE" and payload["stages"][-1]["skipped"] == "no JSON changed"
    assert not list(tmp_path.rglob("*.zip"))


def test_run_that_produces_json_exits_zero_and_packages(tmp_path, capsys):
    ingestor = FakeIngestor()
    ingestor.write_page = ingestor.write_acta = True
    code, _ = run(tmp_path, "run", "--source", "fctt", "--season", "2026-2027", "--package", "--json",
                  ingestor=ingestor)
    payload = json.loads(capsys.readouterr().out)
    assert code == EXIT_OK and payload["outcome"] == "SUCCEEDED"
    assert payload["changes"] == {"contentChanged": 1, "actasChanged": 1}
    assert list(tmp_path.rglob("*.zip"))


def test_failed_download_without_content_exits_four(tmp_path, capsys):
    ingestor = FakeIngestor()
    ingestor.download_exit = 1
    code, _ = run(tmp_path, "run", "--source", "fctt", "--season", "2026-2027", "--json", ingestor=ingestor)
    payload = json.loads(capsys.readouterr().out)
    assert code == EXIT_SOURCE_UNAVAILABLE == 4
    assert payload["outcome"] == "SOURCE_UNAVAILABLE" and payload["retryable"] is True
    assert payload["status"] == "FAILED" and [stage["stage"] for stage in payload["stages"]] == ["DOWNLOAD"]
    assert [r.stages for r in ingestor.requests] == [(IngestStage.DOWNLOAD, IngestStage.PARSE)]


def test_failed_download_that_wrote_content_exits_one(tmp_path):
    ingestor = FakeIngestor()
    ingestor.download_exit, ingestor.write_page, ingestor.write_acta = 1, True, True
    code, _ = run(tmp_path, "run", "--source", "fctt", "--season", "2026-2027", ingestor=ingestor)
    assert code == EXIT_ISSUES


def test_text_summary_shows_outcome_changes_and_skipped_stage(tmp_path, capsys):
    run(tmp_path, "run", "--source", "fctt", "--season", "2026-2027", "--package")
    output = capsys.readouterr().out
    assert "outcome=NO_CHANGES, retryable=false" in output
    assert "changes: contentChanged=0, actasChanged=0" in output
    assert "PACKAGE: skipped (no JSON changed)" in output


def test_help_exits_zero(tmp_path):
    with pytest.raises(SystemExit) as exit_:
        build_parser().parse_args(["--help"])
    assert exit_.value.code == 0
