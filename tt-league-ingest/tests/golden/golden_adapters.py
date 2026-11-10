"""Adapter that runs the unified ``ingest_fcttweb`` package against the golden harness' fake site.

The stored expected outputs were produced by the original ``ingest_bcnesa`` / ``ingest_fctt`` ports (git history of
``tests/golden/expected`` and of those packages); the adapter freezes the clock and replaces the HTTP session.
"""

from __future__ import annotations

import contextlib
import importlib
from datetime import datetime, timezone
from typing import Iterator
from unittest import mock

import requests

from golden_harness import TIMEOUT, Reply, Router

# 2026-10-02 12:00 in Madrid (10:00 UTC), the instant every golden run believes it is.
FROZEN_UTC = datetime(2026, 10, 2, 10, 0, 0, tzinfo=timezone.utc)


class FrozenDatetime(datetime):
    @classmethod
    def now(cls, tz=None):
        if tz is None:
            return cls(2026, 10, 2, 12, 0, 0)
        moment = FROZEN_UTC.astimezone(tz)
        return cls(moment.year, moment.month, moment.day, moment.hour, moment.minute, moment.second, tzinfo=moment.tzinfo)


class FakeResponse:
    """The parts of ``requests.Response`` the downloaders use."""

    def __init__(self, reply: Reply, url: str):
        self.status_code = reply.status
        self.content = reply.body
        self.headers = {"Content-Type": reply.content_type}
        self.reason = "error"  # what the legacy urllib fake reports as the HTTP error reason
        self.url = url

    @property
    def text(self) -> str:
        return self.content.decode("utf-8", "replace")


@contextlib.contextmanager
def requests_transport(router: Router) -> Iterator[None]:
    def fake_get(self, url, headers=None, timeout=None, **_):
        reply = router.respond(url)
        if reply == TIMEOUT:
            raise requests.Timeout("timed out")
        return FakeResponse(reply, url)

    with mock.patch.object(requests.Session, "get", fake_get), mock.patch("time.sleep", lambda _: None):
        yield


class FcttWebAdapter:
    name = "fcttweb"

    def __init__(self) -> None:
        self.clock = importlib.import_module("ingest_fcttweb.clock")
        self.ingestor = importlib.import_module("ingest_fcttweb.ingestor")

    @contextlib.contextmanager
    def installed(self, router: Router) -> Iterator[None]:
        with requests_transport(router), mock.patch.object(self.clock, "datetime", FrozenDatetime):
            yield

    def download(self, source: str, argv: list[str]) -> int:
        return self.ingestor.download_main(source, argv)

    def parse(self, source: str, argv: list[str]) -> int:
        return self.ingestor.parse_main(source, argv)
