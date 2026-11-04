"""Side channel counting final source failures of the legacy downloaders and parsers.

The legacy scripts return only an exit code, so they report each URL or document that finally failed (after their
own retries) through ``http_error()``, ``timeout()`` and ``parse_error()``. The calls do nothing unless a collector
installed by ``collect()`` is active, which is the case when a script runs standalone.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass


@dataclass
class HealthCounters:
    http_errors: int = 0
    timeouts: int = 0
    parse_errors: int = 0


_collector: ContextVar[HealthCounters | None] = ContextVar("ingest_health_collector", default=None)


@contextmanager
def collect() -> Iterator[HealthCounters]:
    """Install a fresh collector for the duration of the block and yield it."""
    counters = HealthCounters()
    token = _collector.set(counters)
    try:
        yield counters
    finally:
        _collector.reset(token)


def http_error() -> None:
    active = _collector.get()
    if active is not None:
        active.http_errors += 1


def timeout() -> None:
    active = _collector.get()
    if active is not None:
        active.timeouts += 1


def parse_error() -> None:
    active = _collector.get()
    if active is not None:
        active.parse_errors += 1
