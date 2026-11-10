"""Adapters that run an implementation of the fctt.cat pipelines against the golden harness' fake site.

``FcttWebAdapter`` drives the unified ``ingest_fcttweb`` package (the one under test). ``LegacyAdapter`` drives the
original ``ingest_bcnesa`` / ``ingest_fctt`` ports; it only works while those packages are installed and was used
to produce the stored expected outputs.
"""

from __future__ import annotations

import contextlib
import email.message
import importlib
import io
from datetime import datetime, timezone
from typing import Any, Iterator
from unittest import mock
from urllib.error import HTTPError

import requests

from golden_harness import HTML_TYPE, TIMEOUT, Reply, Router

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


class FakeUrllibResponse(io.BytesIO):
    def __init__(self, reply: Reply):
        super().__init__(reply.body)
        self.status = reply.status
        self.headers = {"Content-Type": reply.content_type}


class FakeOpener:
    def __init__(self, router: Router):
        self.router = router

    def open(self, request, timeout=None):
        url = request.full_url
        reply = self.router.respond(url)
        if reply == TIMEOUT:
            raise TimeoutError("timed out")
        if reply.status >= 400:
            raise HTTPError(url, reply.status, "error", email.message.Message(), None)
        return FakeUrllibResponse(reply)


class LegacyAdapter:
    name = "legacy"

    def __init__(self) -> None:
        self.modules = {
            "bcnesa": {"download": importlib.import_module("ingest_bcnesa.download"),
                       "parse": importlib.import_module("ingest_bcnesa.parse")},
            "fctt": {"download": importlib.import_module("ingest_fctt.download"),
                     "parse": importlib.import_module("ingest_fctt.parse")},
        }

    @contextlib.contextmanager
    def installed(self, router: Router) -> Iterator[None]:
        bcnesa, fctt = self.modules["bcnesa"], self.modules["fctt"]
        with contextlib.ExitStack() as stack:
            stack.enter_context(requests_transport(router))
            stack.enter_context(mock.patch.object(fctt["download"], "build_opener", lambda *_: FakeOpener(router)))
            for module in (bcnesa["download"], fctt["download"], fctt["parse"]):
                stack.enter_context(mock.patch.object(module, "datetime", FrozenDatetime))
            yield

    def download(self, source: str, argv: list[str]) -> int:
        return self.modules[source]["download"].main(argv)

    def parse(self, source: str, argv: list[str]) -> int:
        return self.modules[source]["parse"].main(argv)


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
