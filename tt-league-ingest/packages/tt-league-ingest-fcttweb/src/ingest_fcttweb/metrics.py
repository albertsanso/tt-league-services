"""Persistent download metrics, one store per legacy JSON shape (file name and layout are kept per profile).

Both stores are fed by the same ``FetchClient`` (through the ``Recorder`` protocol of ``ingest_fcttweb.http``) and
both learn from earlier runs to size the retry budget and the pacing:

* ``RequestLogMetrics`` (BCNESA) keeps one record per logical request, per-target and per-match-day history and a
  retry budget derived from the observed retry success rate.
* ``AttemptLogMetrics`` (FCTT) keeps one record per attempt, latency moments, per-league and per-match-day
  outcomes and tunes delay and backoff from the recent failure rate.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import math
import re
import time
import uuid
from collections import Counter
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from ingest_fcttweb import clock
from ingest_fcttweb.files import atomic_write
from ingest_fcttweb.http import Fetched

# --------------------------------------------------------------------------- request-log model (BCNESA)

METRICS_VERSION = 1
MAX_RUNS = 100
MAX_REQUEST_LOG = 3000
MAX_FAILURE_LOG = 500
MAX_DAY_HISTORY = 10
MIN_RETRY_SAMPLES = 10  # Retry attempts needed before the observed success rate is trusted.
TARGET_FAILURE_RATE = 0.05  # Retry until at most this share of failing requests stays failed.
MAX_EXTRA_RETRIES = 2  # Adaptive retries never exceed --retry by more than this.
RATE_LIMIT_ERRORS = {"HTTP 429", "HTTP 503"}


def error_class(message: str) -> str:
    """Group error messages for the error distribution (``HTTP 500``, ``timeout``...)."""
    lowered = message.casefold()
    if match := re.match(r"http (\d{3})", lowered):
        return f"HTTP {match.group(1)}"
    for needle, name in (
        ("incomplete content", "incomplete content"), ("empty page", "empty page"), ("without matches", "empty page"),
        ("not a pdf", "not a PDF"), ("timed out", "timeout"), ("timeout", "timeout"),
        ("connection", "connection error"), ("ssl", "SSL error"),
    ):
        if needle in lowered:
            return name
    return "other"


def new_kind_stats() -> dict[str, Any]:
    return {
        "requests": 0, "successes": 0, "incomplete": 0, "failures": 0, "attempts": 0, "retries": 0,
        "retry_successes": 0, "attempts_to_success": {}, "errors": {},
        "duration_s": {"count": 0, "sum": 0.0, "sum_sq": 0.0, "min": None, "max": None},
    }


class RequestLogMetrics:
    """Success/failure metrics persisted across runs and the retry policy learnt from them.

    The JSON file holds lifetime totals per request kind (attempt histogram, error distribution, duration moments),
    rolling logs of recent requests and failures, per-target and per-match-day history, and a summary of the last
    runs.
    """

    robots_cache = None  # robots.txt is not cached in this shape

    def __init__(self, path: Path, logger: logging.Logger):
        self.path = path
        self.logger = logger
        self.data = self._load()
        self.run_counters: Counter = Counter()
        self.run_requests = 0
        self.run_retries = 0
        self.started_at = clock.local_iso()
        self.started = time.monotonic()

    def _empty(self) -> dict[str, Any]:
        return {
            "schema_version": METRICS_VERSION, "created_at": clock.local_iso(), "updated_at": None,
            "totals": {}, "request_kinds": {}, "targets": {}, "match_days": {},
            "runs": [], "recent_requests": [], "recent_failures": [],
        }

    def _load(self) -> dict[str, Any]:
        empty = self._empty()
        if not self.path.exists():
            return empty
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as exc:
            backup = self.path.with_suffix(".corrupt.json")
            self.logger.warning("Metrics file %s is unreadable (%s); moved to %s and starting fresh", self.path, exc, backup)
            self.path.replace(backup)
            return empty
        if not isinstance(data, dict) or data.get("schema_version") != METRICS_VERSION:
            self.logger.warning("Metrics file %s has an unknown schema; starting fresh", self.path)
            return empty
        for name, value in empty.items():
            data.setdefault(name, value)
        return data

    # ---- recording

    def attempt(self, *_: Any) -> None:
        """Attempts are not logged one by one in this shape."""

    def exhausted(self) -> None:
        """Nor is the exhausted retry budget."""

    def request(self, fetched: Fetched, kind: str, key: str) -> None:
        self.run_requests += 1
        self.run_retries += fetched.retries
        stats = self.data["request_kinds"].setdefault(kind, new_kind_stats())
        stats["requests"] += 1
        stats["attempts"] += fetched.attempts
        stats["retries"] += fetched.retries
        if fetched.outcome == "ok":
            stats["successes"] += 1
            histogram = stats["attempts_to_success"]
            histogram[str(fetched.attempts)] = histogram.get(str(fetched.attempts), 0) + 1
            if fetched.attempts > 1:
                stats["retry_successes"] += 1
        elif fetched.outcome == "incomplete":
            stats["incomplete"] += 1
        else:
            stats["failures"] += 1
        for error in fetched.errors:
            name = error_class(error)
            stats["errors"][name] = stats["errors"].get(name, 0) + 1
        duration = stats["duration_s"]
        duration["count"] += 1
        duration["sum"] += fetched.duration_s
        duration["sum_sq"] += fetched.duration_s ** 2
        duration["min"] = fetched.duration_s if duration["min"] is None else min(duration["min"], fetched.duration_s)
        duration["max"] = fetched.duration_s if duration["max"] is None else max(duration["max"], fetched.duration_s)

        target = self.data["targets"].setdefault(f"{kind}:{key}", {
            "url": fetched.url, "requests": 0, "failures": 0, "retries": 0, "consecutive_failures": 0,
        })
        target["requests"] += 1
        target["retries"] += fetched.retries
        target["last_seen"] = clock.local_iso()
        target["last_outcome"] = fetched.outcome
        if fetched.outcome in ("ok", "incomplete"):
            target["consecutive_failures"] = 0
        else:
            target["failures"] += 1
            target["consecutive_failures"] += 1
            target["last_error"] = fetched.errors[-1] if fetched.errors else fetched.outcome

        entry = {"at": clock.local_iso(), "kind": kind, "key": key, "url": fetched.url, "attempts": fetched.attempts,
                 "duration_s": round(fetched.duration_s, 3), "outcome": fetched.outcome, "errors": list(fetched.errors)}
        self._append("recent_requests", entry, MAX_REQUEST_LOG)
        if fetched.outcome != "ok":
            self._append("recent_failures", entry, MAX_FAILURE_LOG)

    def record_match_day(self, key: str, outcome: str, retries: int = 0, duration_s: float = 0.0,
                         error: str | None = None, issues: list[str] | None = None) -> None:
        """Record a match day outcome: new, updated, unchanged, future, skipped, incomplete or failed."""
        self.run_counters[f"match days {outcome}"] += 1
        day = self.data["match_days"].setdefault(key, {
            "downloads": 0, "failures": 0, "skipped": 0, "incomplete": 0, "retries": 0, "history": [],
        })
        if outcome in ("new", "updated", "unchanged", "future"):
            day["downloads"] += 1
        elif outcome == "skipped":
            day["skipped"] += 1
        elif outcome == "incomplete":
            day["incomplete"] += 1
        elif outcome == "failed":
            day["failures"] += 1
        day["retries"] += retries
        day["last_outcome"] = outcome
        day["last_retries"] = retries
        day["last_duration_s"] = round(duration_s, 3)
        day["updated_at"] = clock.local_iso()
        if error:
            day["last_error"] = error
        day["last_issues"] = issues or []
        signature = hashlib.sha1("\n".join(sorted(issues)).encode("utf-8")).hexdigest()[:12] if issues else None
        day["history"] = (day["history"] + [{
            "at": clock.local_iso(), "outcome": outcome, "retries": retries,
            "duration_s": round(duration_s, 3), "issues_signature": signature,
        }])[-MAX_DAY_HISTORY:]

    def count(self, name: str, amount: int = 1) -> None:
        self.run_counters[name] += amount

    def _append(self, name: str, entry: dict[str, Any], limit: int) -> None:
        self.data[name] = (self.data[name] + [entry])[-limit:]

    # ---- learning

    def retry_success_rate(self, kind: str) -> tuple[float, int] | None:
        """Share of retry attempts that succeeded for ``kind`` over the recent request window.

        Each retry is modelled as an independent trial (a geometric distribution), so the success rate tells how
        many retries are needed to recover most failing requests.
        """
        recent = [entry for entry in self.data["recent_requests"] if entry["kind"] == kind]
        retry_attempts = sum(max(0, entry["attempts"] - 1) for entry in recent)
        if retry_attempts < MIN_RETRY_SAMPLES:
            return None
        recovered = sum(1 for entry in recent if entry["outcome"] == "ok" and entry["attempts"] > 1)
        return recovered / retry_attempts, retry_attempts

    def advised_retries(self, kind: str, base: int) -> int:
        """Retries so that at most ``TARGET_FAILURE_RATE`` of failing requests stay failed.

        Bounded to [1, base + MAX_EXTRA_RETRIES] so the server is never hammered; ``--retry 0`` is always honoured.
        """
        estimate = self.retry_success_rate(kind)
        if base == 0 or estimate is None:
            return base
        rate, _ = estimate
        if rate >= 0.999:
            needed = 1
        elif rate <= 0.01:
            needed = 1  # Retries barely ever help: keep a single one.
        else:
            needed = math.ceil(math.log(TARGET_FAILURE_RATE) / math.log(1 - rate))
        return max(1, min(base + MAX_EXTRA_RETRIES, needed))

    def rate_limited(self) -> bool:
        """True when recent failures are dominated by rate limiting (HTTP 429/503)."""
        errors = [error_class(error) for entry in self.data["recent_failures"] for error in entry["errors"]]
        errors += [error_class(error) for entry in self.data["recent_requests"][-500:]
                   if entry["outcome"] == "ok" for error in entry["errors"]]
        if len(errors) < 10:
            return False
        return sum(error in RATE_LIMIT_ERRORS for error in errors) / len(errors) > 0.2

    def persistently_incomplete(self, key: str) -> bool:
        """True when the last two runs ended with the same incomplete content for this match day."""
        history = self.data["match_days"].get(key, {}).get("history", [])
        last = history[-2:]
        return (len(last) == 2 and all(item["outcome"] == "incomplete" for item in last)
                and last[0]["issues_signature"] == last[1]["issues_signature"])

    def problem_report(self, limit: int = 5) -> list[str]:
        """Targets and match days that failed most often, for the run log."""
        lines = []
        targets = sorted(
            ((name, value) for name, value in self.data["targets"].items() if value["failures"]),
            key=lambda item: (item[1]["consecutive_failures"], item[1]["failures"]), reverse=True,
        )
        for name, value in targets[:limit]:
            lines.append(f"{name}: {value['failures']}/{value['requests']} failed "
                         f"({value['consecutive_failures']} in a row), last error: {value.get('last_error')}")
        days = sorted(
            ((name, value) for name, value in self.data["match_days"].items() if value["failures"] or value["incomplete"]),
            key=lambda item: item[1]["failures"] + item[1]["incomplete"], reverse=True,
        )
        for name, value in days[:limit]:
            lines.append(f"{name}: {value['failures']} failed, {value['incomplete']} incomplete, "
                         f"{value['retries']} retries; last: {value['last_outcome']}")
        return lines

    # ---- persistence

    def save(self, args: argparse.Namespace, policy: dict[str, Any]) -> None:
        totals = Counter(self.data["totals"])
        totals.update(self.run_counters)
        totals.update({"runs": 1, "requests": self.run_requests, "retries": self.run_retries})
        self.data["totals"] = dict(sorted(totals.items()))
        run = {
            "started_at": self.started_at,
            "finished_at": clock.local_iso(),
            "duration_s": round(time.monotonic() - self.started, 1),
            "arguments": {name: (str(value) if isinstance(value, Path) else sorted(value) if isinstance(value, set) else value)
                          for name, value in vars(args).items()},
            "policy": policy,
            "requests": self.run_requests,
            "retries": self.run_retries,
            "counters": dict(sorted(self.run_counters.items())),
        }
        self._append("runs", run, MAX_RUNS)
        self.data["updated_at"] = clock.local_iso()
        atomic_write(self.path, json.dumps(self.data, ensure_ascii=False, indent=1).encode("utf-8"))
        self.logger.info("Metrics saved to %s", self.path)


# --------------------------------------------------------------------------- attempt-log model (FCTT)

HISTORY_LIMIT = 20
RUNS_LIMIT = 100
CHRONIC_FAILURES = 3
THROTTLE_KINDS = ("http_429", "http_503")


class Tuning:
    """Adjustments learned from the metrics of previous runs."""

    def __init__(self) -> None:
        self.delay_factor = 1.0
        self.backoff_factor = 1.0
        self.chronic_match_days: set[str] = set()
        self.chronic_leagues: set[str] = set()
        self.notes: list[str] = []

    def as_dict(self) -> dict[str, Any]:
        return {"delay_factor": round(self.delay_factor, 3), "backoff_factor": round(self.backoff_factor, 3),
                "chronic_match_days": sorted(self.chronic_match_days),
                "chronic_leagues": sorted(self.chronic_leagues), "notes": self.notes}


def _empty_attempt_metrics() -> dict[str, Any]:
    return {
        "schema_version": 1,
        "updated_at": None,
        "totals": {"runs": 0, "requests": 0, "failed_requests": 0, "retries": 0, "successful_downloads": 0,
                   "failed_downloads": 0, "skipped_match_days": 0},
        "requests": {
            "latency_seconds": {"count": 0, "mean": 0.0, "m2": 0.0, "min": None, "max": None},
            "attempts_until_success": {},
            "exhausted": 0,
            "errors_by_kind": {},
            "errors_by_status": {},
            "errors_by_hour_utc": {},
            "by_host": {},
        },
        "leagues": {},
        "match_days": {},
        "runs": [],
        "robots": {},
    }


def _bump(counter: dict[str, int], key: Any, amount: int = 1) -> None:
    counter[str(key)] = counter.get(str(key), 0) + amount


class AttemptLogMetrics:
    """Persistent JSON metrics shared across runs: one entry per request attempt."""

    def __init__(self, path: Path, logger: logging.Logger):
        self.path = path
        self.logger = logger
        self.data = self._load()
        self.run: dict[str, Any] = {}

    @property
    def robots_cache(self) -> dict[str, Any]:
        return self.data.setdefault("robots", {})

    def _load(self) -> dict[str, Any]:
        if not self.path.exists():
            return _empty_attempt_metrics()
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
            if data.get("schema_version") != 1:
                raise ValueError(f"unsupported schema_version {data.get('schema_version')}")
        except (OSError, ValueError) as error:
            backup = self.path.with_name(f"{self.path.stem}.corrupt-{int(time.time())}{self.path.suffix}")
            self.logger.warning("Metrics file %s unreadable (%s); moved to %s", self.path, error, backup)
            self.path.replace(backup)
            return _empty_attempt_metrics()
        base = _empty_attempt_metrics()
        for key, value in base.items():
            data.setdefault(key, value)
        for key, value in base["requests"].items():
            data["requests"].setdefault(key, value)
        return data

    def tuning(self, adaptive: bool = True) -> Tuning:
        tuning = Tuning()
        if not adaptive:
            return tuning
        recent = self.data["runs"][-10:]
        requests = sum(run["counters"].get("requests", 0) for run in recent)
        failed = sum(run["counters"].get("failed_requests", 0) for run in recent)
        if requests >= 20:
            rate = failed / requests
            tuning.delay_factor = 1.0 + min(2.0, 3 * rate)
            tuning.backoff_factor = 1.0 + min(3.0, 4 * rate)
            tuning.notes.append(f"recent request failure rate {rate:.1%} over {requests} requests")
        throttled = sum(run.get("errors_by_kind", {}).get(kind, 0) for run in recent[-3:] for kind in THROTTLE_KINDS)
        if throttled:
            tuning.delay_factor *= 1.5
            tuning.backoff_factor *= 1.5
            tuning.notes.append(f"{throttled} throttling responses (429/503) in the last runs: slowing down")
        histogram = self.data["requests"]["attempts_until_success"]
        rescued = sum(count for attempt, count in histogram.items() if int(attempt) > 1)
        exhausted = self.data["requests"]["exhausted"]
        if rescued + exhausted >= 20:
            effectiveness = rescued / (rescued + exhausted)
            tuning.notes.append(f"retries rescued {effectiveness:.0%} of failing requests historically")
            if effectiveness < 0.1:
                tuning.notes.append("retries rarely help; consider a lower --retry")
        tuning.chronic_match_days = {key for key, value in self.data["match_days"].items()
                                     if value.get("consecutive_failures", 0) >= CHRONIC_FAILURES}
        tuning.chronic_leagues = {key for key, value in self.data["leagues"].items()
                                  if value.get("consecutive_failures", 0) >= CHRONIC_FAILURES}
        if tuning.chronic_match_days or tuning.chronic_leagues:
            tuning.notes.append(f"{len(tuning.chronic_match_days)} match day(s) and {len(tuning.chronic_leagues)} "
                                f"league(s) failed in {CHRONIC_FAILURES}+ consecutive runs: retry budget reduced to 1")
        return tuning

    def start_run(self, arguments: dict[str, Any], tuning: Tuning) -> None:
        self.run = {
            "run_id": uuid.uuid4().hex[:12], "started_at": clock.utc_iso(), "finished_at": None, "duration_s": None,
            "_started": time.monotonic(), "arguments": arguments, "tuning": tuning.as_dict(),
            "counters": {"requests": 0, "failed_requests": 0, "retries": 0, "successful_downloads": 0,
                         "failed_downloads": 0, "skipped_match_days": 0, "pdf_downloaded": 0, "pdf_failed": 0,
                         "pdf_skipped": 0, "bytes": 0},
            "outcomes": {}, "errors_by_kind": {}, "failures": [],
        }

    def count(self, name: str, amount: int = 1) -> None:
        if self.run:
            _bump(self.run["counters"], name, amount)

    def request(self, fetched: Fetched, kind: str, key: str) -> None:
        """Requests are logged attempt by attempt in this shape."""

    def attempt(self, url: str, attempt: int, ok: bool, kind: str | None, status: int | None,
                elapsed: float, size: int) -> None:
        requests = self.data["requests"]
        latency = requests["latency_seconds"]
        latency["count"] += 1
        delta = elapsed - latency["mean"]
        latency["mean"] += delta / latency["count"]
        latency["m2"] += delta * (elapsed - latency["mean"])
        latency["min"] = elapsed if latency["min"] is None else min(latency["min"], elapsed)
        latency["max"] = elapsed if latency["max"] is None else max(latency["max"], elapsed)
        host = requests["by_host"].setdefault(urlparse(url).netloc, {"requests": 0, "failures": 0})
        host["requests"] += 1
        self.count("requests")
        self.count("bytes", size)
        if attempt > 1:
            self.count("retries")
        if ok:
            _bump(requests["attempts_until_success"], attempt)
            return
        host["failures"] += 1
        self.count("failed_requests")
        _bump(requests["errors_by_kind"], kind)
        _bump(requests["errors_by_hour_utc"], clock.utc_hour())
        if status is not None:
            _bump(requests["errors_by_status"], status)
        if self.run:
            _bump(self.run["errors_by_kind"], kind)

    def exhausted(self) -> None:
        self.data["requests"]["exhausted"] += 1

    def record_league(self, key: str, outcome: str, error: str | None = None, jornadas: int | None = None) -> None:
        league = self.data["leagues"].setdefault(key, {"consecutive_failures": 0, "outcomes": {}})
        failed = outcome == "failed"
        league["consecutive_failures"] = league["consecutive_failures"] + 1 if failed else 0
        league["last_outcome"], league["last_run_at"] = outcome, clock.utc_iso()
        if jornadas is not None:
            league["jornadas"] = jornadas
        if error:
            league["last_error"] = error
        _bump(league["outcomes"], outcome)
        if failed and self.run:
            self.run["failures"].append({"key": key, "error": error})

    def record_match_day(self, key: str, *, url: str, outcome: str, status: str | None, attempts: int,
                         duration: float, errors: list[str]) -> None:
        entry = self.data["match_days"].setdefault(key, {"url": url, "consecutive_failures": 0, "outcomes": {},
                                                         "total_attempts": 0, "history": []})
        failed = outcome in ("failed", "incomplete")
        entry["consecutive_failures"] = entry["consecutive_failures"] + 1 if failed else 0
        entry.update(url=url, last_outcome=outcome, last_run_at=clock.utc_iso())
        if status:
            entry["last_status"] = status
        entry["total_attempts"] += attempts
        _bump(entry["outcomes"], outcome)
        entry["history"] = (entry["history"] + [{
            "run_id": self.run.get("run_id"), "at": clock.utc_iso(), "outcome": outcome, "status": status,
            "attempts": attempts, "retries": max(0, attempts - 1), "duration_s": round(duration, 3),
            "errors": errors[:10],
        }])[-HISTORY_LIMIT:]
        if not self.run:
            return
        _bump(self.run["outcomes"], outcome)
        if outcome in ("downloaded", "updated", "unchanged", "saved_minimal"):
            self.count("successful_downloads")
        elif failed:
            self.count("failed_downloads")
            if outcome == "failed":
                self.count("skipped_match_days")
            self.run["failures"].append({"key": key, "url": url, "error": errors[-1] if errors else None})
        else:
            self.count("skipped_match_days")

    def finish_run(self) -> dict[str, Any]:
        run = self.run
        run["finished_at"] = clock.utc_iso()
        run["duration_s"] = round(time.monotonic() - run.pop("_started"), 3)
        totals = self.data["totals"]
        totals["runs"] += 1
        for name in ("requests", "failed_requests", "retries", "successful_downloads", "failed_downloads",
                     "skipped_match_days"):
            totals[name] += run["counters"][name]
        self.data["runs"] = (self.data["runs"] + [run])[-RUNS_LIMIT:]
        self.data["updated_at"] = run["finished_at"]
        self.save()
        return run

    def save(self) -> None:
        atomic_write(self.path, json.dumps(self.data, ensure_ascii=False, indent=2).encode("utf-8"))
