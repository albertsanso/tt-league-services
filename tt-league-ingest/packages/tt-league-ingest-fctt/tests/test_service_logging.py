"""The legacy FCTT scripts leave the root logger and the console alone while the REST service logs."""

from __future__ import annotations

import logging

import pytest

from ingest_fctt import download, parse
from ingest_common import logs

SCRIPTS = {
    "download": (lambda log_file: download.configure_logging(log_file, False) or download.LOGGER,
                 "fctt-incremental-download"),
    "parse": (lambda log_file: parse.configure_logging(log_file, False) or parse.LOGGER, "fctt-incremental-parse"),
}


class Capture(logging.Handler):
    def __init__(self) -> None:
        super().__init__(logging.DEBUG)
        self.records: list[logging.LogRecord] = []

    def emit(self, record: logging.LogRecord) -> None:
        self.records.append(record)


@pytest.fixture(autouse=True)
def isolated_logging():
    root = logging.getLogger()
    saved = (list(root.handlers), root.level, logging.getLogRecordFactory(), logs._installed_factory,
             logs._service_logging)
    scripts = [logging.getLogger(name) for _, name in SCRIPTS.values()]
    script_state = [(list(s.handlers), s.level, s.propagate) for s in scripts]
    yield
    for logger in [root, *scripts]:
        for handler in list(logger.handlers):
            logger.removeHandler(handler)
            if isinstance(handler, logging.FileHandler):
                handler.close()
    handlers, level, factory, installed, active = saved
    for handler in handlers:
        root.addHandler(handler)
    root.setLevel(level)
    logging.setLogRecordFactory(factory)
    logs._installed_factory, logs._service_logging = installed, active
    for logger, (script_handlers, script_level, propagate) in zip(scripts, script_state):
        for handler in script_handlers:
            logger.addHandler(handler)
        logger.setLevel(script_level)
        logger.propagate = propagate


def clear_script_handlers() -> None:
    for _, name in SCRIPTS.values():
        script = logging.getLogger(name)
        for handler in list(script.handlers):
            script.removeHandler(handler)


@pytest.mark.parametrize("script", SCRIPTS)
def test_service_mode_keeps_the_root_logger_and_adds_no_console_handler(script, tmp_path):
    configure, name = SCRIPTS[script]
    logs.configure_service_logging("json")
    root = logging.getLogger()
    before = list(root.handlers)
    clear_script_handlers()
    log_file = tmp_path / "logs" / "script.log"

    logger = configure(log_file)
    logger.info("written to the file")

    assert root.handlers == before
    assert not [h for h in logger.handlers if type(h) is logging.StreamHandler]
    file_handlers = [h for h in logger.handlers if isinstance(h, logging.FileHandler)]
    assert len(file_handlers) == 1
    file_handlers[0].flush()
    assert "written to the file" in log_file.read_text(encoding="utf-8")
    assert logger.name == name


@pytest.mark.parametrize("script", SCRIPTS)
def test_a_second_call_leaves_exactly_one_file_handler(script, tmp_path):
    configure, _ = SCRIPTS[script]
    logs.configure_service_logging("json")
    clear_script_handlers()

    configure(tmp_path / "first.log")
    first = [h for h in logging.getLogger(SCRIPTS[script][1]).handlers if isinstance(h, logging.FileHandler)]
    logger = configure(tmp_path / "second.log")

    handlers = [h for h in logger.handlers if isinstance(h, logging.FileHandler)]
    assert len(handlers) == 1
    assert handlers[0].baseFilename.endswith("second.log")
    assert first[0].stream is None  # the earlier handler was closed


@pytest.mark.parametrize("script", SCRIPTS)
def test_records_reach_the_root_logger_with_the_bound_ids(script, tmp_path):
    configure, _ = SCRIPTS[script]
    logs.configure_service_logging("json")
    clear_script_handlers()
    capture = Capture()
    logging.getLogger().addHandler(capture)
    logger = configure(tmp_path / "script.log")

    with logs.bind_run("ingest-7", "orchestrator-7"):
        logger.info("inside the run")

    record = next(r for r in capture.records if r.getMessage() == "inside the run")
    assert record.runId == "orchestrator-7" and record.ingestRunId == "ingest-7"


@pytest.mark.parametrize("script", SCRIPTS)
def test_standalone_still_adds_a_console_handler_and_stops_propagating(script, tmp_path):
    configure, _ = SCRIPTS[script]
    logs._service_logging = False
    clear_script_handlers()

    logger = configure(tmp_path / "standalone.log")

    assert any(type(h) is logging.StreamHandler for h in logger.handlers)
    assert any(isinstance(h, logging.FileHandler) for h in logger.handlers)
    assert logger.propagate is False
