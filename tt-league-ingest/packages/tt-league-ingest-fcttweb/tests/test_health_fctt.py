from __future__ import annotations

import logging
from pathlib import Path
from types import SimpleNamespace

import pytest
import requests

from ingest_common import health
from ingest_fcttweb import parse as pa
from ingest_fcttweb.http import FetchClient
from ingest_fcttweb.metrics import AttemptLogMetrics
from ingest_fcttweb.profile import FCTT_PROFILE

REAL_CONTENT = Path(__file__).resolve().parent / "fixtures" / "content"
URL = "https://fctt.cat/lligues/grup-1/"


class RaisingSession:
    """robots.txt answers 404 (everything allowed); every other URL raises ``error`` or answers ``status``."""

    def __init__(self, error=None, status=None):
        self.error, self.status = error, status

    def get(self, url, headers=None, timeout=None):
        if url.endswith("/robots.txt"):
            return SimpleNamespace(status_code=404, content=b"", headers={}, reason="Not Found")
        if self.error:
            raise self.error
        return SimpleNamespace(status_code=self.status, content=b"", headers={}, reason="Not Found")


def client(tmp_path, session):
    metrics = AttemptLogMetrics(tmp_path / "metrics.json", logging.getLogger("test-fctt-health"))
    return FetchClient(FCTT_PROFILE.http, metrics, delay=0, backoff=0, session=session, sleep=lambda _: None)


@pytest.mark.parametrize("error", [requests.Timeout("slow"), requests.ConnectTimeout("slow"),
                                   requests.ReadTimeout("slow")])
def test_timeouts_are_counted_once_after_the_retries(tmp_path, error):
    with health.collect() as counters:
        assert not client(tmp_path, RaisingSession(error=error)).get(URL, retries=2).ok
    assert (counters.timeouts, counters.http_errors) == (1, 0)


def test_http_404_is_counted_as_an_http_error(tmp_path):
    with health.collect() as counters:
        assert not client(tmp_path, RaisingSession(status=404)).get(URL, retries=2).ok
    assert (counters.timeouts, counters.http_errors) == (0, 1)


def test_page_that_cannot_be_parsed_counts_a_parse_error(tmp_path, monkeypatch):
    def broken(*_args):
        raise ValueError("broken page")

    monkeypatch.setattr(pa.StatefulParser, "parse_page", broken)
    with health.collect() as counters:
        pa.main(FCTT_PROFILE, ["--input_dir", str(REAL_CONTENT), "--output_dir", str(tmp_path / "out"),
                 "--log_file", str(tmp_path / "parse.log")])
    assert counters.parse_errors >= 1
    assert counters.http_errors == counters.timeouts == 0
    logging.getLogger(FCTT_PROFILE.parse_logger).handlers[:] = [logging.NullHandler()]
