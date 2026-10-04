from __future__ import annotations

import threading
import time

import pytest
from fastapi.testclient import TestClient

from ingest_common.run import IngestStage, StageReport
from ingest_common.settings import IngestSettings
from ingest_common.source import Source
from ingest_rest.app import create_app

KEY = {"X-API-Key": "k"}


class FakeIngestor:
    source = Source.FCTT
    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE})
    supported_filters = frozenset({"category", "group", "phase", "match_days", "gender"})

    def __init__(self, fail=False):
        self.fail = fail
        self.release = threading.Event()
        self.release.set()

    def download(self, request, settings, listener):
        self.release.wait(5)
        report = StageReport(IngestStage.DOWNLOAD)
        report.count("downloaded", 2)
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
