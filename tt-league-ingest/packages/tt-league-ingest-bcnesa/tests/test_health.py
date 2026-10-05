from __future__ import annotations

import logging
from types import SimpleNamespace

import requests

from ingest_bcnesa import download as dl
from ingest_bcnesa import parse
from ingest_common import health


class StubSession:
    """robots.txt answers 404 (everything allowed); every other URL follows ``behaviour``."""

    def __init__(self, behaviour):
        self.behaviour = behaviour

    def get(self, url, headers=None, timeout=None):
        if url.endswith("/robots.txt"):
            return SimpleNamespace(status_code=404, text="")
        if isinstance(self.behaviour, Exception):
            raise self.behaviour
        return self.behaviour


class NoMetrics:
    def record_request(self, record):
        pass


def fetcher(behaviour, retries=0):
    instance = dl.Fetcher({}, retries, 0, 0, 1, logging.getLogger("test-bcnesa-health"), NoMetrics())
    instance.session = StubSession(behaviour)
    return instance


def always(response):
    return response


def test_timeout_is_counted_as_a_timeout():
    with health.collect() as counters:
        assert fetcher(requests.Timeout("slow")).get("https://example.test/a", always, "page") is None
    assert (counters.timeouts, counters.http_errors) == (1, 0)


def test_http_404_is_counted_as_an_http_error():
    response = SimpleNamespace(status_code=404, headers={}, content=b"")
    with health.collect() as counters:
        assert fetcher(response).get("https://example.test/a", always, "page") is None
    assert (counters.timeouts, counters.http_errors) == (0, 1)


def test_exhausted_retryable_status_is_one_http_error():
    response = SimpleNamespace(status_code=503, headers={}, content=b"")
    with health.collect() as counters:
        assert fetcher(response, retries=2).get("https://example.test/a", always, "page") is None
    assert (counters.timeouts, counters.http_errors) == (0, 1)


def test_jornada_without_matches_counts_a_parse_error(tmp_path):
    page = tmp_path / "in" / "2026-2027" / "preferent" / "g1" / "1a-fase" / "jornada_01.html"
    page.parent.mkdir(parents=True)
    page.write_text("<html><body></body></html>", encoding="utf-8")
    with health.collect() as counters:
        code = parse.main(["--season", "2026-2027", "--input_dir", str(tmp_path / "in"),
                           "--output_dir", str(tmp_path / "out"), "--log_file", str(tmp_path / "parse.log")])
    assert counters.parse_errors == 1
    assert code == 1
