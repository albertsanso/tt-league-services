"""Run-correlated logging for the REST service (standard library only).

``bind_run`` ties the ids of one ingest run to every log record created in its context. ``install_record_factory``
copies them onto each ``LogRecord`` when it is created, so they exist whatever handler formats the record, including
the legacy per-script file handlers. ``JsonFormatter`` writes one JSON object per line with the field names the
orchestrator also writes (logstash style): ``runId`` is the *orchestrator* run id (the request's ``correlationId``)
and ``ingestRunId`` is this service's own run id.
"""

from __future__ import annotations

import json
import logging
import sys
from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from datetime import datetime, timezone

_run: ContextVar[tuple[str, str | None] | None] = ContextVar("ingest_log_run", default=None)
_service_logging = False
_installed_factory: object | None = None

TEXT_FORMAT = "%(asctime)s %(levelname)s [%(runId)s %(ingestRunId)s] %(name)s %(message)s"


@contextmanager
def bind_run(ingest_run_id: str, correlation_id: str | None) -> Iterator[None]:
    """Attach the ids to every log record created in this context (and in threads that copy the context)."""
    token = _run.set((ingest_run_id, correlation_id))
    try:
        yield
    finally:
        _run.reset(token)


def install_record_factory() -> None:
    """Set ``runId`` and ``ingestRunId`` on every new ``LogRecord``; calling it again changes nothing."""
    global _installed_factory
    current = logging.getLogRecordFactory()
    if current is _installed_factory:
        return
    previous = current

    def factory(*args: object, **kwargs: object) -> logging.LogRecord:
        record = previous(*args, **kwargs)
        bound = _run.get()
        record.runId = bound[1] if bound else None
        record.ingestRunId = bound[0] if bound else None
        return record

    logging.setLogRecordFactory(factory)
    _installed_factory = factory


class JsonFormatter(logging.Formatter):
    """One JSON object per line: ``@timestamp``, ``level``, ``logger_name``, ``thread_name``, ``message``, ids."""

    def format(self, record: logging.LogRecord) -> str:
        moment = datetime.fromtimestamp(record.created, tz=timezone.utc)
        entry: dict[str, object] = {
            "@timestamp": moment.strftime("%Y-%m-%dT%H:%M:%S.") + f"{moment.microsecond // 1000:03d}Z",
            "level": record.levelname,
            "logger_name": record.name,
            "thread_name": record.threadName,
            "message": record.getMessage(),
        }
        run_id = getattr(record, "runId", None)
        ingest_run_id = getattr(record, "ingestRunId", None)
        if run_id is not None:
            entry["runId"] = run_id
        if ingest_run_id is not None:
            entry["ingestRunId"] = ingest_run_id
        if record.exc_info:
            entry["stack_trace"] = self.formatException(record.exc_info)
        return json.dumps(entry, ensure_ascii=False)


def configure_service_logging(fmt: str) -> None:
    """Replace the root handlers with one stderr handler (``json`` or ``text``) and mark service logging active."""
    global _service_logging
    if fmt not in ("json", "text"):
        raise ValueError(f"log format must be json or text, got {fmt!r}")
    install_record_factory()
    handler = logging.StreamHandler(sys.stderr)
    handler.setLevel(logging.INFO)
    handler.setFormatter(JsonFormatter() if fmt == "json" else logging.Formatter(TEXT_FORMAT))
    root = logging.getLogger()
    for existing in list(root.handlers):
        root.removeHandler(existing)
    root.addHandler(handler)
    root.setLevel(logging.INFO)
    _service_logging = True


def service_logging_active() -> bool:
    """True once the REST service configured logging; the legacy scripts then leave the root logger alone."""
    return _service_logging


def attach_script_file_handler(logger: logging.Logger, log_file, fmt: str, level: int = logging.INFO) -> None:
    """Service mode for a legacy script: its own file handler only, never a console handler or the root logger.

    The handler of an earlier in-process call is removed and closed, so repeated runs do not leak file handles. The
    records still propagate to the root JSON handler, which adds ``runId`` and ``ingestRunId``.
    """
    for existing in [h for h in logger.handlers if getattr(h, "_ingest_script_file", False)]:
        logger.removeHandler(existing)
        existing.close()
    logger.setLevel(level)
    logger.propagate = True
    if log_file is None:
        return
    log_file.parent.mkdir(parents=True, exist_ok=True)
    handler = logging.FileHandler(log_file, encoding="utf-8")
    handler.setLevel(level)
    handler.setFormatter(logging.Formatter(fmt))
    handler._ingest_script_file = True  # type: ignore[attr-defined]
    logger.addHandler(handler)
