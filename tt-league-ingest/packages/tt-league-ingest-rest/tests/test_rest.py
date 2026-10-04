from __future__ import annotations

import hashlib
import json
import threading
import time
import zipfile
from io import BytesIO
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import ingest_rest.app

from ingest_common.run import IngestStage, StageReport
from ingest_common.scan import record_exit_code
from ingest_common.settings import IngestSettings
from ingest_common.source import Source
from ingest_rest.app import create_app

KEY = {"X-API-Key": "k"}


class FakeIngestor:
    source = Source.FCTT
    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE})
    supported_filters = frozenset({"category", "group", "phase", "match_days", "gender"})

    def __init__(self, fail=False, download_exit=0, write_page=False, raises=False):
        self.fail, self.download_exit, self.write_page, self.raises = fail, download_exit, write_page, raises
        self.release = threading.Event()
        self.release.set()

    def download(self, request, settings, listener):
        self.release.wait(5)
        if self.raises:
            raise RuntimeError("unexpected")
        report = StageReport(IngestStage.DOWNLOAD)
        report.count("downloaded", 2)
        if self.write_page:
            page = settings.content_dir(request.source) / str(request.season) / "jornada_01.html"
            page.parent.mkdir(parents=True, exist_ok=True)
            page.write_text("<html/>", encoding="utf-8")
        record_exit_code(report, "fake download", self.download_exit)
        if self.fail:
            report.fail("fake", "boom")
        return report

    def parse(self, request, settings, listener):
        return StageReport(IngestStage.PARSE)


def client_for(tmp_path, ingestor):
    app = create_app(IngestSettings(tmp_path), "k", {Source.FCTT: ingestor})
    return TestClient(app)


def wait_for(client, run_id, done=("SUCCEEDED", "FAILED", "COMPLETED_WITH_ISSUES")):
    for _ in range(100):
        body = client.get(f"/api/v1/ingest/runs/{run_id}", headers=KEY).json()
        if body["status"] in done:
            return body
        time.sleep(0.05)
    raise AssertionError(body)


def post(client, **overrides):
    payload = {"source": "fctt", "season": "2026-2027", "stages": ["download", "parse"], **overrides}
    return client.post("/api/v1/ingest/runs", json=payload, headers=KEY)


def test_api_key_is_required_except_for_health(tmp_path):
    client = client_for(tmp_path, FakeIngestor())
    assert client.get("/health").status_code == 200
    assert client.get("/api/v1/ingest/runs").status_code == 401
    assert client.get("/api/v1/ingest/runs", headers={"X-API-Key": "wrong"}).status_code == 401
    assert client.post("/api/v1/ingest/runs", json={}).status_code == 401


def test_startup_requires_an_api_key(tmp_path):
    with pytest.raises(ValueError):
        create_app(IngestSettings(tmp_path), "", {})


def test_create_and_poll_lifecycle(tmp_path):
    client = client_for(tmp_path, FakeIngestor())
    response = post(client)
    assert response.status_code == 202
    body = wait_for(client, response.json()["runId"])
    assert body["status"] == "SUCCEEDED" and body["source"] == "FCTT"
    assert body["stages"][0]["counters"]["downloaded"] == 2
    listing = client.get("/api/v1/ingest/runs", headers=KEY).json()
    assert [run["runId"] for run in listing] == [body["runId"]]


@pytest.mark.parametrize("overrides", [
    {"source": "nope"}, {"season": "2026-2028"}, {"stages": []}, {"stages": ["bogus"]},
    {"mode": "delta"}, {"mode": "other"}, {"filters": {"matchDays": "0"}}])
def test_invalid_input_is_rejected(tmp_path, overrides):
    assert post(client_for(tmp_path, FakeIngestor()), **overrides).status_code == 400


def test_same_source_conflict_while_active(tmp_path):
    ingestor = FakeIngestor()
    ingestor.release.clear()
    client = client_for(tmp_path, ingestor)
    first = post(client)
    assert first.status_code == 202
    assert post(client).status_code == 409
    ingestor.release.set()
    wait_for(client, first.json()["runId"])
    assert post(client).status_code == 202


def test_unknown_run_is_404(tmp_path):
    assert client_for(tmp_path, FakeIngestor()).get("/api/v1/ingest/runs/nope", headers=KEY).status_code == 404


def test_failed_stage_is_reported_as_failed(tmp_path):
    client = client_for(tmp_path, FakeIngestor(fail=True))
    body = wait_for(client, post(client).json()["runId"])
    assert body["status"] == "FAILED"
    assert body["stages"][0]["issues"] == [{"where": "fake", "message": "boom"}]


def test_successful_run_exposes_outcome_retryable_and_changes(tmp_path):
    client = client_for(tmp_path, FakeIngestor(write_page=True))
    body = wait_for(client, post(client).json()["runId"])
    assert body["outcome"] == "SUCCEEDED" and body["retryable"] is False
    assert body["changes"] == {"contentChanged": 1, "actasChanged": 0}
    assert [stage["skipped"] for stage in body["stages"]] == [None, None]


def test_outcome_is_null_while_the_run_is_active(tmp_path):
    ingestor = FakeIngestor()
    ingestor.release.clear()
    client = client_for(tmp_path, ingestor)
    run_id = post(client).json()["runId"]
    active = client.get(f"/api/v1/ingest/runs/{run_id}", headers=KEY).json()
    assert active["outcome"] is None and active["retryable"] is False and active["changes"] == {}
    ingestor.release.set()
    assert wait_for(client, run_id)["outcome"] == "SUCCEEDED"


def test_run_with_no_json_changes_reports_no_changes_and_skipped_package(tmp_path):
    client = client_for(tmp_path, FakeIngestor())
    body = wait_for(client, post(client, stages=["download", "parse", "package"]).json()["runId"])
    assert body["status"] == "SUCCEEDED" and body["outcome"] == "NO_CHANGES" and body["retryable"] is False
    assert body["changes"] == {"contentChanged": 0, "actasChanged": 0}
    assert body["stages"][-1]["stage"] == "PACKAGE" and body["stages"][-1]["skipped"] == "no JSON changed"
    assert body["package"] is None


def test_failed_download_without_content_is_source_unavailable_and_retryable(tmp_path):
    client = client_for(tmp_path, FakeIngestor(download_exit=1))
    body = wait_for(client, post(client).json()["runId"])
    assert body["status"] == "FAILED" and body["outcome"] == "SOURCE_UNAVAILABLE" and body["retryable"] is True
    assert [stage["stage"] for stage in body["stages"]] == ["DOWNLOAD"]


def test_failed_stage_outcome_is_failed_and_not_retryable(tmp_path):
    client = client_for(tmp_path, FakeIngestor(fail=True))
    body = wait_for(client, post(client).json()["runId"])
    assert body["outcome"] == "FAILED" and body["retryable"] is False


def test_pipeline_exception_is_recorded_as_failed_outcome(tmp_path):
    client = client_for(tmp_path, FakeIngestor(raises=True))
    body = wait_for(client, post(client).json()["runId"])
    assert body["status"] == "FAILED" and body["outcome"] == "FAILED" and body["retryable"] is False
    assert "RuntimeError: unexpected" in body["error"]


# --------------------------------------------------------------------------- packages

PACKAGE_URL = "/api/v1/ingest/runs/{}/package"


def write_acta(tmp_path, payload):
    acta = IngestSettings(tmp_path).actas_json_dir(Source.FCTT) / "2026-2027" / "g1" / "acta.json"
    acta.parent.mkdir(parents=True, exist_ok=True)
    acta.write_text(json.dumps(payload), encoding="utf-8")


def packaged_manifest(content: bytes) -> dict:
    with zipfile.ZipFile(BytesIO(content)) as archive:
        return json.loads(archive.read("manifest.json"))


def package_run(client):
    run_id = post(client, stages=["package"]).json()["runId"]
    return run_id, wait_for(client, run_id)


def test_package_endpoint_streams_the_runs_zip_with_its_hash(tmp_path):
    write_acta(tmp_path, {"jornada": 1, "acta_publicada": False})
    client = client_for(tmp_path, FakeIngestor())
    run_id, body = package_run(client)
    assert body["status"] == "SUCCEEDED"
    assert Path(body["package"]) == IngestSettings(tmp_path).packages_dir(Source.FCTT) / "runs" / f"{run_id}.zip"
    response = client.get(PACKAGE_URL.format(run_id), headers=KEY)
    assert response.status_code == 200
    assert response.headers["content-type"] == "application/zip"
    assert response.headers["x-content-sha256"] == hashlib.sha256(response.content).hexdigest()
    manifest = packaged_manifest(response.content)
    assert manifest["runId"] == run_id and manifest["generator"] == "tt-league-ingest"
    assert manifest["matchCounts"] == {"expected": 1, "withResult": 0, "pending": 1}


def test_each_run_keeps_its_own_package(tmp_path):
    write_acta(tmp_path, {"jornada": 1, "acta_publicada": False})
    client = client_for(tmp_path, FakeIngestor())
    first, _ = package_run(client)
    write_acta(tmp_path, {"jornada": 1, "acta_publicada": True})
    second, _ = package_run(client)
    first_manifest = packaged_manifest(client.get(PACKAGE_URL.format(first), headers=KEY).content)
    second_manifest = packaged_manifest(client.get(PACKAGE_URL.format(second), headers=KEY).content)
    assert first_manifest["runId"] == first and first_manifest["matchCounts"]["pending"] == 1
    assert second_manifest["runId"] == second and second_manifest["matchCounts"]["pending"] == 0
    assert first_manifest["contentSha256"] != second_manifest["contentSha256"]


def test_package_endpoint_status_codes(tmp_path):
    ingestor = FakeIngestor()
    client = client_for(tmp_path, ingestor)
    assert client.get(PACKAGE_URL.format("nope"), headers=KEY).status_code == 404
    no_package = post(client).json()["runId"]  # download + parse only
    wait_for(client, no_package)
    response = client.get(PACKAGE_URL.format(no_package), headers=KEY)
    assert response.status_code == 404 and response.json()["detail"] == "the run produced no package"
    assert client.get(PACKAGE_URL.format(no_package)).status_code == 401
    ingestor.release.clear()
    active = post(client, stages=["download", "package"]).json()["runId"]
    assert client.get(PACKAGE_URL.format(active), headers=KEY).status_code == 409
    ingestor.release.set()
    wait_for(client, active)


def test_skipped_package_stage_has_no_package(tmp_path):
    client = client_for(tmp_path, FakeIngestor())
    run_id = post(client, stages=["download", "parse", "package"]).json()["runId"]
    assert wait_for(client, run_id)["stages"][-1]["skipped"] == "no JSON changed"
    assert client.get(PACKAGE_URL.format(run_id), headers=KEY).status_code == 404


def test_packages_beyond_the_history_limit_are_deleted(tmp_path, monkeypatch):
    monkeypatch.setattr(ingest_rest.app, "HISTORY_LIMIT", 1)
    write_acta(tmp_path, {"jornada": 1})
    client = client_for(tmp_path, FakeIngestor())
    first, first_body = package_run(client)
    second, _ = package_run(client)
    assert not Path(first_body["package"]).exists()
    response = client.get(PACKAGE_URL.format(first), headers=KEY)
    assert response.status_code == 404 and response.json()["detail"] == "the run's package is no longer retained"
    assert client.get(PACKAGE_URL.format(second), headers=KEY).status_code == 200


# --------------------------------------------------------------------------- scopes

SCOPES = [{"category": "tdm", "group": "g1", "matchDays": [3, 4]}, {"gender": "female", "matchDays": "2-3"}]


def test_scoped_run_is_accepted_and_reports_its_scopes(tmp_path):
    client = client_for(tmp_path, FakeIngestor())
    response = post(client, scopes=SCOPES + [SCOPES[0]])
    assert response.status_code == 202
    body = wait_for(client, response.json()["runId"])
    assert body["status"] == "SUCCEEDED"
    assert body["scopes"] == [
        {"category": "tdm", "group": "g1", "phase": None, "territory": None, "gender": None, "matchDays": [3, 4]},
        {"category": None, "group": None, "phase": None, "territory": None, "gender": "female", "matchDays": [2, 3]}]


def test_filter_run_reports_its_filters_as_the_only_scope(tmp_path):
    client = client_for(tmp_path, FakeIngestor())
    body = wait_for(client, post(client, filters={"group": "g2"}).json()["runId"])
    assert [scope["group"] for scope in body["scopes"]] == ["g2"]


@pytest.mark.parametrize("overrides, message", [
    ({"filters": {}, "scopes": SCOPES}, "either filters or scopes"),
    ({"scopes": []}, "must not be empty"),
    ({"scopes": [{"category": " "}]}, "scope 1"),
    ({"scopes": [{"group": "g1"}, {"matchDays": "0"}]}, "scope 2"),
    ({"scopes": SCOPES, "stages": ["parse", "package"]}, "delta"),
])
def test_invalid_scopes_are_rejected(tmp_path, overrides, message):
    response = post(client_for(tmp_path, FakeIngestor()), **overrides)
    assert response.status_code == 400 and message in response.json()["detail"]


def test_unknown_scope_key_is_a_request_validation_error(tmp_path):
    assert post(client_for(tmp_path, FakeIngestor()), scopes=[{"grup": "g1"}]).status_code == 422


def test_scoped_delta_without_match_days_is_accepted(tmp_path):
    assert post(client_for(tmp_path, FakeIngestor()), scopes=[{"group": "g1"}], mode="delta").status_code == 202


def test_unsupported_scope_field_fails_the_run_before_any_stage(tmp_path):
    client = client_for(tmp_path, FakeIngestor())
    body = wait_for(client, post(client, scopes=[{"group": "g1"}, {"territory": "catalunya"}]).json()["runId"])
    assert body["status"] == "FAILED" and body["outcome"] == "FAILED"
    assert body["stages"][0]["issues"][0]["message"] == "FCTT does not support filter(s): territory (scope 2)"


# --------------------------------------------------------------------------- match-day status

STATUS_URL = "/api/v1/ingest/sources/{}/match-days-status"


def write_status(tmp_path):
    from datetime import datetime, timezone

    from ingest_common.match_day_status import MatchDayEntry, MatchState, ScanResult, write_status_report

    def entry(season, day):
        return MatchDayEntry(season, day, f"{season}/tdm/g1/regular/jornada-{day}.html",
                             (MatchState(True, True),), datetime(2026, 10, 4, tzinfo=timezone.utc), category="tdm")

    content = IngestSettings(tmp_path).content_dir(Source.FCTT)
    write_status_report(content, Source.FCTT, ScanResult([entry("2026-2027", 1), entry("2026-2027", 2),
                                                          entry("2025-2026", 9)]))


def test_status_endpoint_returns_the_file_and_filters_by_season(tmp_path):
    write_status(tmp_path)
    client = client_for(tmp_path, FakeIngestor())
    whole = client.get(STATUS_URL.format("fctt"), headers=KEY)
    assert whole.status_code == 200
    assert whole.json()["source"] == "FCTT" and whole.json()["summary"]["matchDays"] == 3
    season = client.get(STATUS_URL.format("FCTT"), params={"season": "2026-2027"}, headers=KEY).json()
    assert season["seasons"] == ["2026-2027"] and [day["matchDay"] for day in season["matchDays"]] == [1, 2]
    assert season["summary"]["matchDays"] == 2 and season["summary"]["complete"] == 2


def test_status_endpoint_errors(tmp_path):
    client = client_for(tmp_path, FakeIngestor())
    assert client.get(STATUS_URL.format("fctt")).status_code == 401
    assert client.get(STATUS_URL.format("fctt"), headers=KEY).status_code == 404  # no download yet
    write_status(tmp_path)
    assert client.get(STATUS_URL.format("fctt"), params={"season": "2024-2025"}, headers=KEY).status_code == 404
    assert client.get(STATUS_URL.format("fctt"), params={"season": "2026-2028"}, headers=KEY).status_code == 400
    assert client.get(STATUS_URL.format("nope"), headers=KEY).status_code == 400
    assert client.get(STATUS_URL.format("rfetm"), headers=KEY).status_code == 404  # other source, no file


def test_status_endpoint_answers_while_a_run_is_active(tmp_path):
    write_status(tmp_path)
    ingestor = FakeIngestor()
    ingestor.release.clear()
    client = client_for(tmp_path, ingestor)
    run_id = post(client).json()["runId"]
    assert client.get(STATUS_URL.format("fctt"), headers=KEY).status_code == 200
    ingestor.release.set()
    wait_for(client, run_id)
