from __future__ import annotations

import json
import threading
import zipfile
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path, PurePosixPath

import pytest

from ingest_common.packaging import PackagingError, build_manifest, package_season
from ingest_common.pipeline import IngestPipeline
from ingest_common.run import (IngestFilters, IngestRequest, IngestStage, RunOutcome, RunStatus,
                               StageReport)
from ingest_common.scan import record_exit_code
from ingest_common.season import Season
from ingest_common.settings import IngestSettings
from ingest_common.source import Source
from ingest_common.upload import PlatformUploader, UploadError

SEASON = Season.parse("2026-2027")


def write_actas(root: Path, days: dict[str, int]) -> Path:
    season_dir = root / "2026-2027"
    for name, day in days.items():
        path = season_dir / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"jornada": day, "acta_publicada": True}), encoding="utf-8")
    return season_dir


def test_manifest_key_set():
    manifest = build_manifest(Source.FCTT, [SEASON], {"ACTAS": ["b", "a"]}, "delta")
    assert manifest == {"source": "FCTT", "seasons": ["2026-2027"], "assets": {"ACTAS": {"files": ["a", "b"]}},
                        "mode": "delta"}
    assert "mode" not in build_manifest(Source.RFETM, [SEASON], {"TEAMS": ["x"]})
    with pytest.raises(PackagingError):
        build_manifest(Source.FCTT, [SEASON], {"ACTAS": []}, "DELTA")
    with pytest.raises(PackagingError):
        build_manifest(Source.FCTT, [SEASON], {"OTHER": []})


def test_snapshot_round_trip_includes_every_file_and_teams(tmp_path):
    actas = write_actas(tmp_path / "actas-json", {"tdm/g1/jornada-1.json": 1, "tdm/g1/jornada-2.json": 2})
    teams = tmp_path / "2026-2027.json"
    teams.write_text("[]", encoding="utf-8")
    result = package_season(source=Source.RFETM, season=SEASON, actas_dir=actas, teams_file=teams,
                            output=tmp_path / "out" / "p.zip")
    with zipfile.ZipFile(result.zip_path) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        names = set(archive.namelist())
    assert manifest["assets"]["ACTAS"]["files"] == ["actas-json/2026-2027/tdm/g1/jornada-1.json",
                                                    "actas-json/2026-2027/tdm/g1/jornada-2.json"]
    assert manifest["assets"]["TEAMS"]["files"] == ["equipos-json/2026-2027.json"]
    assert "mode" in manifest and manifest["mode"] == "snapshot"
    assert names == {"manifest.json", *manifest["assets"]["ACTAS"]["files"], "equipos-json/2026-2027.json"}


def test_delta_selects_by_payload_jornada(tmp_path):
    actas = write_actas(tmp_path / "a", {"x/acta_1_2.json": 3, "x/acta_3_4.json": 4, "y/acta_5_6.json": 3})
    result = package_season(source=Source.RFETM, season=SEASON, actas_dir=actas, teams_file=None,
                            output=tmp_path / "d.zip", mode="delta", match_days=frozenset({3}))
    assert result.manifest["assets"]["ACTAS"]["files"] == ["actas-json/2026-2027/x/acta_1_2.json",
                                                            "actas-json/2026-2027/y/acta_5_6.json"]
    assert result.manifest["mode"] == "delta"


def test_delta_select_replaces_the_match_day_rule(tmp_path):
    actas = write_actas(tmp_path / "a", {"g1/r/acta_1.json": 3, "g1/r/acta_2.json": 4, "g2/r/acta_3.json": 3,
                                         "g3/r/acta_4.json": 3})
    seen = []

    def select(relative, day):
        seen.append((relative, day))
        return (relative.parts[0], day) in {("g1", 3), ("g2", 3)}

    result = package_season(source=Source.FCTT, season=SEASON, actas_dir=actas, teams_file=None,
                            output=tmp_path / "d.zip", mode="delta", select=select)
    assert result.manifest["assets"]["ACTAS"]["files"] == ["actas-json/2026-2027/g1/r/acta_1.json",
                                                            "actas-json/2026-2027/g2/r/acta_3.json"]
    assert (PurePosixPath("g1/r/acta_2.json"), 4) in seen
    snapshot = package_season(source=Source.FCTT, season=SEASON, actas_dir=actas, teams_file=None,
                              output=tmp_path / "s.zip", select=lambda relative, day: False)
    assert len(snapshot.manifest["assets"]["ACTAS"]["files"]) == 4  # a snapshot is the whole season
    with pytest.raises(PackagingError, match="no actas selected"):
        package_season(source=Source.FCTT, season=SEASON, actas_dir=actas, teams_file=None,
                       output=tmp_path / "e.zip", mode="delta", select=lambda relative, day: False)


def test_empty_delta_and_existing_output_fail(tmp_path):
    actas = write_actas(tmp_path / "a", {"x/acta.json": 1})
    with pytest.raises(PackagingError, match="no actas selected"):
        package_season(source=Source.FCTT, season=SEASON, actas_dir=actas, teams_file=None,
                       output=tmp_path / "d.zip", mode="delta", match_days=frozenset({9}))
    with pytest.raises(PackagingError, match="requires at least one match day"):
        package_season(source=Source.FCTT, season=SEASON, actas_dir=actas, teams_file=None,
                       output=tmp_path / "d.zip", mode="delta")
    out = tmp_path / "s.zip"
    package_season(source=Source.FCTT, season=SEASON, actas_dir=actas, teams_file=None, output=out)
    with pytest.raises(PackagingError, match="already exists"):
        package_season(source=Source.FCTT, season=SEASON, actas_dir=actas, teams_file=None, output=out)
    package_season(source=Source.FCTT, season=SEASON, actas_dir=actas, teams_file=None, output=out, force=True)


def test_dry_run_writes_nothing_and_exclude_include(tmp_path):
    actas = write_actas(tmp_path / "a", {"x/keep.json": 1, "g2/drop.json": 1})
    out = tmp_path / "s.zip"
    result = package_season(source=Source.FCTT, season=SEASON, actas_dir=actas, teams_file=None, output=out,
                            exclude=("g2",), dry_run=True)
    assert not out.exists() and not result.written
    assert result.manifest["assets"]["ACTAS"]["files"] == ["actas-json/2026-2027/x/keep.json"]


class UploadHandler(BaseHTTPRequestHandler):
    status = 200
    seen: list[dict] = []

    def do_POST(self):
        length = int(self.headers["Content-Length"])
        body = self.rfile.read(length)
        UploadHandler.seen.append({"path": self.path, "auth": self.headers.get("Authorization"),
                                   "shrink": b"allowPublishedShrink" in body, "zip": b"PK" in body})
        self.send_response(UploadHandler.status)
        self.end_headers()
        self.wfile.write(b"server message")

    def log_message(self, *args):
        pass


@pytest.fixture
def server():
    UploadHandler.seen = []
    UploadHandler.status = 200
    httpd = HTTPServer(("127.0.0.1", 0), UploadHandler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    yield f"http://127.0.0.1:{httpd.server_port}"
    httpd.shutdown()


def make_zip(tmp_path):
    path = tmp_path / "p.zip"
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("manifest.json", "{}")
    return path


def test_upload_success_sends_bearer_and_shrink_flag(server, tmp_path):
    PlatformUploader(server, "secret").upload(make_zip(tmp_path), allow_published_shrink=True)
    call = UploadHandler.seen[0]
    assert call == {"path": "/api/v1/administration/import/upload", "auth": "Bearer secret", "shrink": True, "zip": True}


@pytest.mark.parametrize("status,label", [(400, "invalid"), (409, "shrink check")])
def test_upload_surfaces_server_message(server, tmp_path, status, label):
    UploadHandler.status = status
    with pytest.raises(UploadError, match=label) as error:
        PlatformUploader(server, "secret").upload(make_zip(tmp_path))
    assert "server message" in str(error.value) and "secret" not in str(error.value)
    assert len(UploadHandler.seen) == 1  # no automatic retry with the shrink override


class FakeIngestor:
    source = Source.FCTT
    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE})
    supported_filters = frozenset({"category", "match_days"})

    def __init__(self, fail_stage=None):
        self.calls, self.fail_stage = [], fail_stage

    def _stage(self, stage):
        self.calls.append(stage)
        report = StageReport(stage)
        if stage is self.fail_stage:
            report.fail("fake", "boom")
        return report

    def download(self, request, settings, listener):
        return self._stage(IngestStage.DOWNLOAD)

    def parse(self, request, settings, listener):
        return self._stage(IngestStage.PARSE)


def request(stages, **filters):
    return IngestRequest(Source.FCTT, SEASON, stages, IngestFilters(**filters))


def test_pipeline_runs_stages_in_order(tmp_path):
    fake = FakeIngestor()
    settings = IngestSettings(tmp_path)
    report = IngestPipeline(settings, {Source.FCTT: fake}).run(
        request((IngestStage.PARSE, IngestStage.DOWNLOAD)))
    assert fake.calls == [IngestStage.DOWNLOAD, IngestStage.PARSE]
    assert report.status is RunStatus.SUCCEEDED


def test_pipeline_stops_at_first_failed_stage(tmp_path):
    fake = FakeIngestor(fail_stage=IngestStage.DOWNLOAD)
    report = IngestPipeline(IngestSettings(tmp_path), {Source.FCTT: fake}).run(
        request((IngestStage.DOWNLOAD, IngestStage.PARSE)))
    assert fake.calls == [IngestStage.DOWNLOAD] and report.status is RunStatus.FAILED


def test_pipeline_rejects_unregistered_source_unsupported_stage_and_filter(tmp_path):
    settings = IngestSettings(tmp_path)
    fake = FakeIngestor()
    assert IngestPipeline(settings, {}).run(request((IngestStage.DOWNLOAD,))).status is RunStatus.FAILED
    teams = IngestPipeline(settings, {Source.FCTT: fake}).run(request((IngestStage.TEAMS,)))
    assert teams.status is RunStatus.FAILED and "TEAMS" in teams.stages[0].issues[0][1]
    group = IngestPipeline(settings, {Source.FCTT: fake}).run(request((IngestStage.DOWNLOAD,), group="G1"))
    assert group.status is RunStatus.FAILED and "group" in group.stages[0].issues[0][1]
    assert fake.calls == []


def test_pipeline_upload_requires_environment_before_any_stage(tmp_path):
    fake = FakeIngestor()
    report = IngestPipeline(IngestSettings(tmp_path), {Source.FCTT: fake}).run(
        request((IngestStage.DOWNLOAD, IngestStage.UPLOAD)))
    assert report.status is RunStatus.FAILED and fake.calls == []


def test_issues_complete_the_run_with_issues(tmp_path):
    class Noisy(FakeIngestor):
        def parse(self, request, settings, listener):
            report = StageReport(IngestStage.PARSE)
            report.count("invalid")
            return report

    report = IngestPipeline(IngestSettings(tmp_path), {Source.FCTT: Noisy()}).run(request((IngestStage.PARSE,)))
    assert report.status is RunStatus.COMPLETED_WITH_ISSUES


class OutcomeIngestor(FakeIngestor):
    """Writes (or not) the files the real scripts would, and reports a legacy download exit code."""

    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE, IngestStage.TEAMS})

    def __init__(self, download_exit=0, page=None, acta=None, parse_fails=False):
        super().__init__()
        self.download_exit, self.page, self.acta, self.parse_fails = download_exit, page, acta, parse_fails

    def download(self, request, settings, listener):
        report = self._stage(IngestStage.DOWNLOAD)
        if self.page is not None:
            page = settings.content_dir(request.source) / str(request.season) / "jornada_01.html"
            page.parent.mkdir(parents=True, exist_ok=True)
            page.write_text(self.page, encoding="utf-8")
        record_exit_code(report, "fake download", self.download_exit)
        return report

    def parse(self, request, settings, listener):
        report = self._stage(IngestStage.PARSE)
        if self.acta is not None:
            acta = settings.actas_json_dir(request.source) / str(request.season) / "g1" / "acta.json"
            acta.parent.mkdir(parents=True, exist_ok=True)
            acta.write_text(json.dumps(self.acta), encoding="utf-8")
        if self.parse_fails:
            report.fail("fake parse", "boom")
        return report


PUBLISH = (IngestStage.DOWNLOAD, IngestStage.PARSE, IngestStage.PACKAGE)
ACTA = {"jornada": 1, "acta_publicada": True}


def run_pipeline(tmp_path, ingestor, stages=PUBLISH, force=False):
    return IngestPipeline(IngestSettings(tmp_path), {Source.FCTT: ingestor}).run(
        IngestRequest(Source.FCTT, SEASON, stages, force=force))


def test_parse_without_json_changes_skips_package_as_no_changes(tmp_path):
    report = run_pipeline(tmp_path, OutcomeIngestor())
    assert report.outcome is RunOutcome.NO_CHANGES and not report.retryable
    assert report.status is RunStatus.SUCCEEDED
    assert report.changes == {"contentChanged": 0, "actasChanged": 0}
    assert report.stages[-1].stage is IngestStage.PACKAGE and report.stages[-1].skipped == "no JSON changed"
    assert "package" not in report.outputs and not list(tmp_path.rglob("*.zip"))


def test_parse_with_a_new_json_packages_and_succeeds(tmp_path):
    report = run_pipeline(tmp_path, OutcomeIngestor(page="<html/>", acta=ACTA))
    assert report.outcome is RunOutcome.SUCCEEDED and not report.retryable
    assert report.changes == {"contentChanged": 1, "actasChanged": 1}
    assert report.stages[-1].skipped is None and Path(report.outputs["package"]).is_file()


def test_repeated_identical_run_is_no_changes_not_a_packaging_failure(tmp_path):
    ingestor = OutcomeIngestor(page="<html/>", acta=ACTA)
    assert run_pipeline(tmp_path, ingestor).outcome is RunOutcome.SUCCEEDED
    second = run_pipeline(tmp_path, ingestor)
    assert second.outcome is RunOutcome.NO_CHANGES and second.changes["actasChanged"] == 0
    assert second.status is RunStatus.SUCCEEDED and second.stages[-1].skipped == "no JSON changed"


def test_force_packages_even_when_no_json_changed(tmp_path):
    ingestor = OutcomeIngestor(acta=ACTA)
    run_pipeline(tmp_path, ingestor)
    forced = run_pipeline(tmp_path, ingestor, force=True)
    assert forced.changes["actasChanged"] == 0
    assert forced.stages[-1].skipped is None and not forced.stages[-1].failed
    assert forced.outcome is RunOutcome.SUCCEEDED


def test_package_only_run_is_never_skipped(tmp_path):
    settings = IngestSettings(tmp_path)
    actas = settings.actas_json_dir(Source.FCTT) / str(SEASON)
    actas.mkdir(parents=True)
    (actas / "acta.json").write_text(json.dumps(ACTA), encoding="utf-8")
    report = IngestPipeline(settings, {Source.FCTT: OutcomeIngestor()}).run(
        IngestRequest(Source.FCTT, SEASON, (IngestStage.PACKAGE,)))
    assert report.stages[0].skipped is None and report.outcome is RunOutcome.SUCCEEDED
    assert Path(report.outputs["package"]).is_file()


def test_failed_download_without_content_is_source_unavailable(tmp_path):
    ingestor = OutcomeIngestor(download_exit=1)
    report = run_pipeline(tmp_path, ingestor)
    assert report.outcome is RunOutcome.SOURCE_UNAVAILABLE and report.retryable
    assert report.status is RunStatus.FAILED
    assert ingestor.calls == [IngestStage.DOWNLOAD]
    assert [stage.stage for stage in report.stages] == [IngestStage.DOWNLOAD]
    assert report.stages[0].source_unavailable and report.stages[0].failed


def test_failed_download_that_wrote_content_completes_with_issues(tmp_path):
    ingestor = OutcomeIngestor(download_exit=1, page="<html/>", acta=ACTA)
    report = run_pipeline(tmp_path, ingestor)
    assert report.outcome is RunOutcome.COMPLETED_WITH_ISSUES and not report.retryable
    assert report.status is RunStatus.COMPLETED_WITH_ISSUES and report.changes["contentChanged"] == 1
    assert ingestor.calls == [IngestStage.DOWNLOAD, IngestStage.PARSE]


def test_clean_download_that_wrote_nothing_is_not_source_unavailable(tmp_path):
    report = run_pipeline(tmp_path, OutcomeIngestor(download_exit=0), stages=(IngestStage.DOWNLOAD,))
    assert report.outcome is RunOutcome.SUCCEEDED and not report.stages[0].source_unavailable


def test_failed_parse_stage_is_failed_and_not_retryable(tmp_path):
    report = run_pipeline(tmp_path, OutcomeIngestor(page="<html/>", parse_fails=True))
    assert report.outcome is RunOutcome.FAILED and not report.retryable and report.status is RunStatus.FAILED
    assert [stage.stage for stage in report.stages] == [IngestStage.DOWNLOAD, IngestStage.PARSE]


def test_invalid_actas_complete_with_issues_and_are_not_retryable(tmp_path):
    class Invalid(OutcomeIngestor):
        def parse(self, request, settings, listener):
            report = super().parse(request, settings, listener)
            report.count("invalid")
            return report

    report = run_pipeline(tmp_path, Invalid(acta=ACTA))
    assert report.outcome is RunOutcome.COMPLETED_WITH_ISSUES and not report.retryable


def test_early_failure_is_failed_and_not_retryable(tmp_path):
    report = run_pipeline(tmp_path, OutcomeIngestor(), stages=(IngestStage.TEAMS, IngestStage.UPLOAD))
    assert report.outcome is RunOutcome.FAILED and not report.retryable


def test_report_dict_exposes_outcome_retryable_changes_and_skipped(tmp_path):
    payload = run_pipeline(tmp_path, OutcomeIngestor()).to_dict()
    assert payload["outcome"] == "NO_CHANGES" and payload["retryable"] is False
    assert payload["changes"] == {"contentChanged": 0, "actasChanged": 0}
    assert [stage["skipped"] for stage in payload["stages"]] == [None, None, "no JSON changed"]
    unavailable = run_pipeline(tmp_path, OutcomeIngestor(download_exit=1)).to_dict()
    assert unavailable["outcome"] == "SOURCE_UNAVAILABLE" and unavailable["retryable"] is True


def test_entry_points_resolve_the_three_sources():
    from ingest_common.pipeline import discover_ingestors
    assert set(discover_ingestors()) == {Source.RFETM, Source.BCNESA, Source.FCTT}


class ScopedIngestor(OutcomeIngestor):
    """Writes actas of three groups and matches scopes on the first folder (the group) and the day."""

    supported_filters = frozenset({"group", "match_days"})

    def __init__(self):
        super().__init__()
        self.requests = []

    def parse(self, request, settings, listener):
        self.requests.append(request)
        report = self._stage(IngestStage.PARSE)
        season_dir = settings.actas_json_dir(request.source) / str(request.season)
        for group, day in (("g1", 3), ("g1", 4), ("g2", 3), ("g3", 3)):
            acta = season_dir / group / "regular" / f"jornada_{day}.json"
            acta.parent.mkdir(parents=True, exist_ok=True)
            acta.write_text(json.dumps({"jornada": day, "acta_publicada": True}), encoding="utf-8")
        return report

    def scope_matches(self, scope, relative, match_day):
        return ((not scope.group or relative.parts[0] == scope.group)
                and (not scope.match_days or match_day in scope.match_days))


def test_scoped_delta_run_packages_only_the_union_of_the_scopes(tmp_path):
    ingestor = ScopedIngestor()
    scopes = (IngestFilters(group="g1", match_days=frozenset({3})), IngestFilters(group="g2"))
    report = IngestPipeline(IngestSettings(tmp_path), {Source.FCTT: ingestor}).run(
        IngestRequest(Source.FCTT, SEASON, PUBLISH, mode="delta", scopes=scopes))
    assert report.outcome is RunOutcome.SUCCEEDED, report.to_dict()
    with zipfile.ZipFile(report.outputs["package"]) as archive:
        manifest = json.loads(archive.read("manifest.json"))
    assert manifest["mode"] == "delta"
    assert manifest["assets"]["ACTAS"]["files"] == ["actas-json/2026-2027/g1/regular/jornada_3.json",
                                                    "actas-json/2026-2027/g2/regular/jornada_3.json"]
    assert ingestor.requests[0].scopes == scopes
    assert report.to_dict()["scopes"] == [
        {"category": None, "group": "g1", "phase": None, "territory": None, "gender": None, "matchDays": [3]},
        {"category": None, "group": "g2", "phase": None, "territory": None, "gender": None, "matchDays": None}]


def test_unsupported_field_in_any_scope_fails_before_any_stage(tmp_path):
    ingestor = ScopedIngestor()
    scopes = (IngestFilters(group="g1"), IngestFilters(group="g2", category="tdm"))
    report = IngestPipeline(IngestSettings(tmp_path), {Source.FCTT: ingestor}).run(
        IngestRequest(Source.FCTT, SEASON, PUBLISH, mode="delta", scopes=scopes))
    assert report.outcome is RunOutcome.FAILED and ingestor.calls == []
    assert report.stages[0].issues[0][1] == "FCTT does not support filter(s): category (scope 2)"


def test_filter_run_keeps_the_match_day_delta_and_reports_its_filters_as_one_scope(tmp_path):
    ingestor = ScopedIngestor()
    report = IngestPipeline(IngestSettings(tmp_path), {Source.FCTT: ingestor}).run(
        IngestRequest(Source.FCTT, SEASON, PUBLISH, IngestFilters(match_days=frozenset({3})), mode="delta"))
    with zipfile.ZipFile(report.outputs["package"]) as archive:
        files = json.loads(archive.read("manifest.json"))["assets"]["ACTAS"]["files"]
    assert len(files) == 3  # every group's day 3: scope_matches is not consulted
    assert [scope["matchDays"] for scope in report.to_dict()["scopes"]] == [[3]]
