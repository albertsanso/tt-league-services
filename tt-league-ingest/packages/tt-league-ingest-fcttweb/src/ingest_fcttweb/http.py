"""One polite fetch client for both sources, driven by the profile's ``HttpPolicy``.

robots.txt, pacing, ``Retry-After``, exponential backoff with jitter and user-agent rotation live here once; what
differs between BCNESA and FCTT (delays, retryable statuses, agent order, how a ``Retry-After`` combines with the
backoff, what an unreadable robots.txt means) is policy data. The caller supplies a ``check`` that accepts or rejects
the content of a response; a rejected response is retried (``ContentRejected.retry``) and the best content seen is
kept (``payload``) so a page that never validates can still be saved as partial.
"""

from __future__ import annotations

import logging
import random
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import timezone
from email.utils import parsedate_to_datetime
from typing import Any, Protocol
from urllib.parse import urlparse
from urllib.robotparser import RobotFileParser

import requests

from ingest_common import health
from ingest_fcttweb.profile import HttpPolicy

LOGGER = logging.getLogger("fcttweb.http")


class ContentRejected(Exception):
    """A response arrived but its content is not acceptable (empty page, failed validation, not a PDF...).

    ``kind`` classifies it for the metrics, ``payload`` is the best content seen (kept when every retry fails) and
    ``retry`` is False for a definitive refusal (a block page, a missing PDF).
    """

    def __init__(self, kind: str, message: str, payload: Any = None, *, retry: bool = True):
        super().__init__(message)
        self.kind = kind
        self.payload = payload
        self.retry = retry


class Recorder(Protocol):
    """Where the client reports what happened; each metrics model keeps what its legacy file shape needs."""

    robots_cache: dict[str, Any] | None

    def attempt(self, url: str, attempt: int, ok: bool, kind: str | None, status: int | None, elapsed: float,
                size: int) -> None: ...

    def request(self, record: "Fetched", kind: str, key: str) -> None: ...

    def exhausted(self) -> None: ...


@dataclass
class Fetched:
    """The outcome of one logical request (all its attempts)."""

    url: str
    # ok; incomplete (rejected content kept as ``payload`` after the last retry); failed; http-error (a status that is
    # not retried); disallowed (robots.txt)
    outcome: str = "failed"
    status: int | None = None
    body: bytes = b""
    content_type: str = ""
    attempts: int = 0
    elapsed: float = 0.0
    payload: Any = None
    kind: str = ""
    message: str = ""
    errors: list[str] = field(default_factory=list)  # one message per failed attempt, cut to 300 characters
    duration_s: float = 0.0

    @property
    def ok(self) -> bool:
        return self.outcome == "ok"

    @property
    def retries(self) -> int:
        return max(0, self.attempts - 1)


def retry_after_seconds(value: str | None, policy: HttpPolicy) -> float | None:
    if not value:
        return None
    try:
        return max(0.0, float(value))
    except ValueError:
        if not policy.retry_after_dates:
            return None
        try:
            return max(0.0, parsedate_to_datetime(value).timestamp() - time.time())
        except (TypeError, ValueError):
            return None


class Pacer:
    """Enforce a minimum interval between requests."""

    def __init__(self, delay: float, sleep: Callable[[float], None] = time.sleep) -> None:
        self.delay = max(0.0, delay)
        self.sleep = sleep
        self.next_at = 0.0

    def wait(self) -> None:
        remaining = self.next_at - time.monotonic()
        if remaining > 0:
            self.sleep(remaining)
        self.next_at = time.monotonic() + self.delay


class FetchClient:
    """Polite HTTP client: robots.txt, pacing, exponential backoff, rotating user agents, metrics."""

    def __init__(self, policy: HttpPolicy, recorder: Recorder, *, retries: dict[str, int] | None = None,
                 default_retries: int = 3, delay: float | None = None, backoff: float | None = None,
                 timeout: float | None = None, session: requests.Session | None = None,
                 sleep: Callable[[float], None] | None = None, logger: logging.Logger | None = None) -> None:
        self.policy = policy
        self.recorder = recorder
        self.retries = retries or {}
        self.default_retries = default_retries
        self.backoff_base = max(0.0, policy.backoff if backoff is None else backoff)
        self.timeout = policy.timeout if timeout is None else timeout
        self.sleep = sleep or (lambda seconds: time.sleep(seconds))
        self.pacer = Pacer(policy.delay if delay is None else delay, self.sleep)
        self.logger = logger or LOGGER
        self.session = session or requests.Session()
        self.robots: dict[str, RobotFileParser | None] = {}
        agents = list(policy.user_agents)
        if policy.user_agent_order == "shuffled":
            agents = random.sample(agents, len(agents))
        self._agents = agents
        self._agent_count = 0

    # ---- request helpers

    def _user_agent(self, attempt: int) -> str:
        if self.policy.user_agent_order == "by-attempt":
            return self.policy.user_agents[(attempt - 1) % len(self.policy.user_agents)]
        agent = self._agents[self._agent_count % len(self._agents)]
        self._agent_count += 1
        return agent

    def _headers(self, user_agent: str, accept: str | None) -> dict[str, str]:
        return {"User-Agent": user_agent, "Accept": accept or self.policy.accept_html,
                "Accept-Language": self.policy.accept_language}

    def _backoff(self, attempt: int, retry_after: str | None) -> float:
        policy = self.policy
        server_delay = retry_after_seconds(retry_after, policy)
        if server_delay is not None:
            server_delay = min(policy.retry_after_cap, server_delay)
            if policy.retry_after_mode == "instead":
                return server_delay
        delay = self.backoff_base * 2 ** (attempt - 1) + random.uniform(0, self.backoff_base)
        if policy.backoff_cap is not None:
            delay = min(policy.backoff_cap, delay)
        return max(delay, server_delay or 0.0)

    # ---- robots.txt

    def allowed(self, url: str) -> bool:
        robots = self._robots_for(url)
        return robots is None or robots.can_fetch("*", url)

    def _robots_for(self, url: str) -> RobotFileParser | None:
        """robots.txt rules of the URL's origin (RFC 9309); None means everything is allowed."""
        parts = urlparse(url)
        origin = f"{parts.scheme}://{parts.netloc}"
        if origin in self.robots:
            return self.robots[origin]
        policy = self.policy.robots
        cache = self.recorder.robots_cache
        parser: RobotFileParser | None = RobotFileParser(f"{origin}/robots.txt")
        text: str | None = None
        problem = ""
        for attempt in range(1, policy.attempts + 1):
            try:
                self.pacer.wait()
                response = self.session.get(
                    f"{origin}/robots.txt", headers=self._headers(self.policy.user_agents[0], policy.accept),
                    timeout=self.timeout)
                status = response.status_code
                if status >= 400:
                    if policy.any_error_means_none or (400 <= status < 500 and status != 429):
                        self.logger.info("robots.txt of %s answers HTTP %s: no restrictions", origin, status)
                        parser = None  # "unavailable": crawling is allowed
                        break
                    problem = f"HTTP {status}"
                else:
                    text = response.content.decode("utf-8", "replace")
                    if cache is not None:
                        from ingest_fcttweb import clock
                        cache[origin] = {"fetched_at": clock.utc_iso(), "body": text}
                    break
            except requests.RequestException as error:
                problem = str(error)
                if policy.unreadable == "allow":
                    self.logger.warning("Could not read %s/robots.txt (%s); assuming allowed", origin, error)
                    parser = None
                    break
            if attempt < policy.attempts:
                self.sleep(self._backoff(attempt, None))
        else:
            if cache is not None and origin in cache:
                self.logger.warning("robots.txt of %s unreachable (%s); using the copy from %s",
                                    origin, problem, cache[origin]["fetched_at"])
                text = cache[origin]["body"]
            else:
                self.logger.error("robots.txt of %s unreachable (%s) and never seen: assuming full disallow",
                                  origin, problem)
                parser.disallow_all = True  # type: ignore[union-attr]
        if parser is not None and text is not None:
            parser.parse(text.splitlines())
        if parser is not None and (crawl_delay := parser.crawl_delay("*")) and float(crawl_delay) > self.pacer.delay:
            self.logger.info("Honouring %s robots.txt crawl delay of %ss", origin, crawl_delay)
            self.pacer.delay = float(crawl_delay)
        self.robots[origin] = parser
        return parser

    # ---- GET

    def get(self, url: str, check: Callable[[bytes, str], Any] | None = None, *, kind: str = "page",
            key: str | None = None, retries: int | None = None, accept: str | None = None) -> Fetched:
        """GET ``url`` with up to ``retries`` extra attempts; ``check`` may reject the content.

        Never raises for network or content problems: the returned ``Fetched`` says how it ended and every call is
        reported to the recorder.
        """
        budget = self.retries.get(kind, self.default_retries) if retries is None else retries
        result = Fetched(url)
        started = time.monotonic()
        try:
            self._get(result, check, budget, accept)
        finally:
            result.duration_s = time.monotonic() - started
            self.recorder.request(result, kind, key or url)
        return result

    def _get(self, result: Fetched, check: Callable[[bytes, str], Any] | None, retries: int, accept: str | None) -> None:
        url, policy = result.url, self.policy
        if not self.allowed(url):
            self.logger.error("Disallowed by robots.txt, skipping: %s", url)
            result.outcome, result.kind, result.message = "disallowed", "robots_disallowed", "disallowed by robots.txt"
            return
        attempts = retries + 1
        for attempt in range(1, attempts + 1):
            result.attempts = attempt
            self.pacer.wait()
            request_started = time.monotonic()
            retry_after: str | None = None
            retriable = True
            status: int | None = None
            payload: Any = None
            size = 0
            try:
                response = self.session.get(
                    url, headers=self._headers(self._user_agent(attempt), accept), timeout=self.timeout)
                status = response.status_code
                if status >= 400:
                    reason = getattr(response, "reason", "") or ""
                    kind = f"http_{status}"
                    message = f"HTTP {status} {reason}".rstrip() if policy.message_includes_reason else f"HTTP {status}"
                    retriable = status in policy.retry_statuses
                    retry_after = response.headers.get("Retry-After")
                else:
                    body, content_type = response.content, response.headers.get("Content-Type", "")
                    size = len(body)
                    payload = check(body, content_type) if check else None
            except ContentRejected as error:
                kind, message, payload, retriable = error.kind, str(error), error.payload, error.retry
            except requests.Timeout as error:
                kind, message = "timeout", str(error) or "timed out"
            except requests.RequestException as error:
                kind = "network"
                message = f"{type(error).__name__}: {error}" if policy.exception_in_message else str(error)
            else:
                if status < 400:
                    elapsed = time.monotonic() - request_started
                    self.recorder.attempt(url, attempt, True, None, status, elapsed, size)
                    result.outcome, result.status, result.body, result.content_type = "ok", status, body, content_type
                    result.payload, result.elapsed, result.kind, result.message = payload, elapsed, "", ""
                    return
            result.status, result.payload, result.kind, result.message = status, payload, kind, message
            result.errors.append(message[:300])
            self.recorder.attempt(url, attempt, False, kind, status, time.monotonic() - request_started, 0)
            if not retriable or attempt == attempts:
                break
            wait = self._backoff(attempt, retry_after)
            self.logger.warning("Attempt %d/%d failed for %s (%s); retrying in %.1fs", attempt, attempts, url,
                                message[:300], wait)
            self.sleep(wait)
        if status is not None and status >= 400 and not retriable:
            result.outcome = "http-error"
            self.logger.error("HTTP %d (not retried): %s", status, url)
        elif result.payload is not None:
            result.outcome = "incomplete"
        else:
            result.outcome = "failed"
            self.logger.error("Failed after %d attempt(s): %s (%s)", result.attempts, url, result.message[:300])
        if retriable:
            self.recorder.exhausted()
        if result.outcome != "incomplete" or policy.health_on_rejected_content:
            health.timeout() if result.kind == "timeout" else health.http_error()
        elif result.outcome == "incomplete":
            self.logger.error("Still incomplete after %d attempt(s), keeping the last page: %s (%s)",
                              attempts, url, result.message[:300])
