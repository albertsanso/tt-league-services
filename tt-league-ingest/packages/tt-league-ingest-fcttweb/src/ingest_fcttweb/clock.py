"""The only place that reads the wall clock, so tests (and the golden harness) can freeze it."""

from __future__ import annotations

from datetime import datetime, timezone

try:
    from zoneinfo import ZoneInfo

    LOCAL_TZ = ZoneInfo("Europe/Madrid")
except Exception:  # tzdata missing: fall back to naive local time
    LOCAL_TZ = None


def local_now() -> datetime:
    """Naive local time."""
    return datetime.now()


def madrid_now() -> datetime:
    """Time in Europe/Madrid (naive local time when tzdata is missing)."""
    return datetime.now(LOCAL_TZ) if LOCAL_TZ else datetime.now()


def local_iso() -> str:
    """``2026-10-02T12:00:00`` (naive local time)."""
    return local_now().isoformat(timespec="seconds")


def utc_iso() -> str:
    """``2026-10-02T10:00:00Z``."""
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def utc_hour() -> int:
    return datetime.now(timezone.utc).hour
