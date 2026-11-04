"""Polite HTTP client: pacing, retries with backoff and encoding fallback."""

from __future__ import annotations

import random
import time
from collections.abc import Callable, Collection
from dataclasses import dataclass

import requests

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)


class HttpError(Exception):
    """The request could not be completed within the retry budget."""


class RequestPacer:
    """Guarantees a minimum delay between consecutive requests."""

    def __init__(self, delay_seconds: float, clock: Callable[[], float] = time.monotonic,
                 sleep: Callable[[float], None] = time.sleep) -> None:
        if delay_seconds < 0:
            raise ValueError("delay_seconds must be >= 0")
        self._delay = delay_seconds
        self._clock = clock
        self._sleep = sleep
        self._last: float | None = None

    def wait(self) -> None:
        if self._last is not None:
            remaining = self._delay - (self._clock() - self._last)
            if remaining > 0:
                self._sleep(remaining)
        self._last = self._clock()


def decode_html(body: bytes) -> str:
    """UTF-8 first, ISO-8859-1 when the bytes are not valid UTF-8 (RFETM pages)."""
    try:
        return body.decode("utf-8")
    except UnicodeDecodeError:
        return body.decode("iso-8859-1")


def retry_after_seconds(value: str | None) -> float | None:
    if value is None:
        return None
    try:
        seconds = float(value.strip())
    except ValueError:
        return None
    return seconds if seconds >= 0 else None


@dataclass(frozen=True)
class HttpResponse:
    status: int
    body: bytes
    url: str


class PoliteHttpClient:
    """GET client with a fixed User-Agent, request pacing and bounded retries.

    ``accept_status`` lists statuses returned to the caller as a success even if not 2xx (RFETM
    answers parseable HTML with 500). ``retry_status`` lists statuses retried with exponential
    backoff + jitter (fctt.cat answers 404/500 intermittently); ``Retry-After`` is honoured.
    Any other non-2xx status fails immediately.
    """

    def __init__(self, *, delay_seconds: float = 2.0, retries: int = 3, backoff_seconds: float = 2.0,
                 timeout: float = 30.0, accept_status: Collection[int] = (), retry_status: Collection[int] = (),
                 session: requests.Session | None = None, pacer: RequestPacer | None = None,
                 sleep: Callable[[float], None] = time.sleep,
                 jitter: Callable[[], float] = lambda: random.uniform(0, 0.5)) -> None:
        if retries < 0:
            raise ValueError("retries must be >= 0")
        self._session = session or requests.Session()
        self._session.headers["User-Agent"] = USER_AGENT
        self._pacer = pacer or RequestPacer(delay_seconds)
        self._retries = retries
        self._backoff = backoff_seconds
        self._timeout = timeout
        self._accept = frozenset(accept_status)
        self._retry = frozenset(retry_status)
        self._sleep = sleep
        self._jitter = jitter

    def get(self, url: str) -> HttpResponse:
        last_problem = "no attempt made"
        for attempt in range(self._retries + 1):
            self._pacer.wait()
            retry_after: float | None = None
            try:
                response = self._session.get(url, timeout=self._timeout)
            except requests.RequestException as error:
                last_problem = f"{type(error).__name__}: {error}"
            else:
                status = response.status_code
                if 200 <= status < 300 or status in self._accept:
                    return HttpResponse(status, response.content, url)
                last_problem = f"HTTP {status}"
                if status not in self._retry and status not in (408, 425, 429, 502, 503, 504):
                    raise HttpError(f"{url}: {last_problem}")
                retry_after = retry_after_seconds(response.headers.get("Retry-After"))
            if attempt < self._retries:
                delay = retry_after if retry_after is not None else self._backoff * (2 ** attempt) + self._jitter()
                self._sleep(delay)
        raise HttpError(f"{url}: {last_problem} after {self._retries + 1} attempt(s)")

    def close(self) -> None:
        self._session.close()
