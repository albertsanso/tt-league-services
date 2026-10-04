from __future__ import annotations

import json

import pytest

from ingest_cli.main import EXIT_ISSUES, EXIT_OK, EXIT_USAGE, build_parser, build_request, main
from ingest_common.run import IngestStage, StageReport
from ingest_common.source import Source


class FakeIngestor:
    source = Source.FCTT
    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE})
    supported_filters = frozenset({"category", "group", "phase", "match_days", "gender", "territory"})
    invalid = 0

    def __init__(self):
        self.requests = []

    def download(self, request, settings, listener):
        self.requests.append(request)
        return StageReport(IngestStage.DOWNLOAD)

    def parse(self, request, settings, listener):
        self.requests.append(request)
        report = StageReport(IngestStage.PARSE)
        report.count("invalid", self.invalid)
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


def test_help_exits_zero(tmp_path):
    with pytest.raises(SystemExit) as exit_:
        build_parser().parse_args(["--help"])
    assert exit_.value.code == 0
