from __future__ import annotations

import shutil
from pathlib import Path
from types import SimpleNamespace

import requests

from ingest_common import health
from ingest_rfetm import download, parse, teams_download

FIXTURES = Path(__file__).parent / "fixtures"


class StubSession:
    def __init__(self, behaviour):
        self.behaviour = behaviour
        self.calls = 0

    def get(self, url, timeout=None):
        self.calls += 1
        if isinstance(self.behaviour, Exception):
            raise self.behaviour
        return self.behaviour


def downloader(behaviour, retries=1):
    instance = download.Downloader(delay=0, retries=retries)
    instance.session = StubSession(behaviour)
    return instance


def raising(error):
    def get(url, timeout=None):
        raise error

    return SimpleNamespace(get=get)


def test_final_timeout_is_counted_once_after_the_retries(monkeypatch):
    monkeypatch.setattr(download.time, "sleep", lambda _: None)
    instance = downloader(requests.Timeout("slow"), retries=2)
    with health.collect() as counters:
        assert download.fetch_html(instance, "https://example.test/a") is None
    assert (counters.timeouts, counters.http_errors) == (1, 0)
    assert instance.session.calls == 3


def test_final_http_status_error_is_an_http_error(monkeypatch):
    monkeypatch.setattr(download.time, "sleep", lambda _: None)
    response = SimpleNamespace(status_code=404, content=b"", headers={})
    with health.collect() as counters:
        assert download.fetch_html(downloader(response), "https://example.test/a") is None
    assert (counters.timeouts, counters.http_errors) == (0, 1)


def test_retried_failure_that_recovers_is_not_counted(monkeypatch):
    monkeypatch.setattr(download.time, "sleep", lambda _: None)
    ok = SimpleNamespace(status_code=200, content=b"<html></html>", headers={})
    with health.collect() as counters:
        assert download.fetch_html(downloader(ok), "https://example.test/a") == "<html></html>"
    assert (counters.timeouts, counters.http_errors) == (0, 0)


def test_teams_download_counts_timeouts_and_http_errors(monkeypatch):
    monkeypatch.setattr(teams_download.time, "sleep", lambda _: None)
    missing = SimpleNamespace(get=lambda url, timeout=None: SimpleNamespace(status_code=404, content=b""))
    with health.collect() as counters:
        assert teams_download.get_page(raising(requests.Timeout("slow")), "https://example.test/t") is None
        assert teams_download.get_page(missing, "https://example.test/t") is None
    assert (counters.timeouts, counters.http_errors) == (1, 1)


def test_unparseable_html_counts_a_parse_error(tmp_path, monkeypatch):
    shutil.copytree(FIXTURES / "content", tmp_path / "content")

    def broken(_html):
        raise ValueError("broken page")

    monkeypatch.setattr(parse, "parse_html_matches", broken)
    with health.collect() as counters:
        parse.main(["--content-dir", str(tmp_path / "content"), "--json-dir", str(tmp_path / "json"),
                    "--season", "2026-2027"])
    assert counters.parse_errors >= 1
    assert counters.http_errors == counters.timeouts == 0
