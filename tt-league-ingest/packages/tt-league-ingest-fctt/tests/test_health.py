from __future__ import annotations

import logging
import socket
from email.message import Message
from pathlib import Path
from urllib.error import HTTPError, URLError

import pytest

from ingest_common import health
from ingest_fctt import download as dl
from ingest_fctt import parse as pa

REAL_CONTENT = Path(__file__).resolve().parent / "fixtures" / "content"
URL = "https://fctt.cat/lligues/grup-1/"


class RaisingOpener:
    def __init__(self, error):
        self.error = error

    def open(self, request, timeout):
        if request.full_url.endswith("/robots.txt"):
            raise HTTPError(request.full_url, 404, "Not Found", Message(), None)
        raise self.error


def client(tmp_path, error):
    metrics = dl.MetricsStore(tmp_path / "metrics.json")
    return dl.HttpClient(metrics, request_delay=0, backoff_base=0, sleep=lambda _: None, opener=RaisingOpener(error))


@pytest.mark.parametrize("error", [TimeoutError("slow"), URLError(TimeoutError("slow")), socket.timeout("slow")])
def test_timeouts_are_counted_once_after_the_retries(tmp_path, error):
    with health.collect() as counters:
        with pytest.raises(dl.FetchError):
            client(tmp_path, error).get(URL, retries=2)
    assert (counters.timeouts, counters.http_errors) == (1, 0)


def test_http_404_is_counted_as_an_http_error(tmp_path):
    with health.collect() as counters:
        with pytest.raises(dl.FetchError):
            client(tmp_path, HTTPError(URL, 404, "Not Found", Message(), None)).get(URL, retries=2)
    assert (counters.timeouts, counters.http_errors) == (0, 1)


def test_page_that_cannot_be_parsed_counts_a_parse_error(tmp_path, monkeypatch):
    def broken(*_args):
        raise ValueError("broken page")

    monkeypatch.setattr(pa, "parse_page", broken)
    with health.collect() as counters:
        pa.main(["--input_dir", str(REAL_CONTENT), "--output_dir", str(tmp_path / "out"),
                 "--log_file", str(tmp_path / "parse.log")])
    assert counters.parse_errors >= 1
    assert counters.http_errors == counters.timeouts == 0
    pa.LOGGER.handlers[:] = [logging.NullHandler()]
