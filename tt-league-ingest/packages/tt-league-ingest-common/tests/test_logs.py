from __future__ import annotations

import json
import logging
import sys
from concurrent.futures import ThreadPoolExecutor

import pytest

from ingest_common import logs


@pytest.fixture(autouse=True)
def restore_logging():
    root = logging.getLogger()
    handlers, level = list(root.handlers), root.level
    factory, installed, active = logging.getLogRecordFactory(), logs._installed_factory, logs._service_logging
    yield
    for handler in list(root.handlers):
        root.removeHandler(handler)
    for handler in handlers:
        root.addHandler(handler)
    root.setLevel(level)
    logging.setLogRecordFactory(factory)
    logs._installed_factory, logs._service_logging = installed, active


def test_json_fields_and_ids_inside_and_outside_bind_run():
    logs.install_record_factory()
    with logs.bind_run("ingest-1", "orch-1"):
        inside = json.loads(logs.JsonFormatter().format(logging.getLogger("a.b").makeRecord(
            "a.b", logging.WARNING, __file__, 1, "n=%d é", (3,), None)))
    outside = json.loads(logs.JsonFormatter().format(logging.getLogger("a.b").makeRecord(
        "a.b", logging.INFO, __file__, 1, "plain", (), None)))

    assert inside["runId"] == "orch-1" and inside["ingestRunId"] == "ingest-1"
    assert inside["level"] == "WARNING" and inside["logger_name"] == "a.b" and inside["message"] == "n=3 é"
    assert inside["@timestamp"].endswith("Z") and "T" in inside["@timestamp"] and "thread_name" in inside
    assert "runId" not in outside and "ingestRunId" not in outside
    assert "stack_trace" not in outside


def test_a_run_without_a_correlation_id_has_no_run_id():
    logs.install_record_factory()
    with logs.bind_run("ingest-2", None):
        entry = json.loads(logs.JsonFormatter().format(logging.makeLogRecord({"msg": "x"})))

    assert "runId" not in entry and entry["ingestRunId"] == "ingest-2"


def test_a_worker_thread_without_its_own_bind_has_no_ids():
    logs.install_record_factory()
    with logs.bind_run("ingest-1", "orch-1"):
        with ThreadPoolExecutor(max_workers=1) as pool:
            made = pool.submit(lambda: logging.makeLogRecord({"msg": "x"})).result()

    assert made.runId is None and made.ingestRunId is None


def test_stack_trace_is_written_for_exc_info():
    try:
        raise RuntimeError("boom")
    except RuntimeError:
        entry = json.loads(logs.JsonFormatter().format(logging.makeLogRecord(
            {"msg": "failed", "exc_info": sys.exc_info()})))

    assert "RuntimeError: boom" in entry["stack_trace"]


def test_the_record_factory_is_installed_once():
    logs.install_record_factory()
    first = logging.getLogRecordFactory()
    logs.install_record_factory()

    assert logging.getLogRecordFactory() is first


def test_configure_service_logging_replaces_the_root_handlers_and_sets_the_flag():
    root = logging.getLogger()
    root.addHandler(logging.NullHandler())

    logs.configure_service_logging("json")

    assert len(root.handlers) == 1
    assert isinstance(root.handlers[0].formatter, logs.JsonFormatter)
    assert logs.service_logging_active()
    logs.configure_service_logging("text")
    assert len(root.handlers) == 1
    assert not isinstance(root.handlers[0].formatter, logs.JsonFormatter)


def test_an_invalid_format_is_rejected():
    with pytest.raises(ValueError):
        logs.configure_service_logging("xml")
