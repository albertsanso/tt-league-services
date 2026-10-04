from __future__ import annotations

import json
import threading
import zipfile
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import pytest

from ingest_common.packaging import PackagingError, build_manifest, package_season
from ingest_common.pipeline import IngestPipeline
from ingest_common.run import IngestFilters, IngestRequest, IngestStage, RunStatus, StageReport
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


def test_entry_points_resolve_the_three_sources():
    from ingest_common.pipeline import discover_ingestors
    assert set(discover_ingestors()) == {Source.RFETM, Source.BCNESA, Source.FCTT}
