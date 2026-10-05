from __future__ import annotations

import logging

import pytest

from ingest_common import logs
from ingest_rest import main as rest_main


@pytest.fixture(autouse=True)
def restore_logging():
    root = logging.getLogger()
    saved = (list(root.handlers), root.level, logging.getLogRecordFactory(), logs._installed_factory,
             logs._service_logging)
    yield
    for handler in list(root.handlers):
        root.removeHandler(handler)
    for handler in saved[0]:
        root.addHandler(handler)
    root.setLevel(saved[1])
    logging.setLogRecordFactory(saved[2])
    logs._installed_factory, logs._service_logging = saved[3], saved[4]


def test_an_invalid_log_format_exits_with_2(tmp_path, monkeypatch, capsys):
    monkeypatch.setenv(rest_main.API_KEY_VARIABLE, "k")
    monkeypatch.setenv("TT_INGEST_DATA_DIR", str(tmp_path))
    monkeypatch.setenv(rest_main.LOG_FORMAT_VARIABLE, "xml")
    monkeypatch.setattr(rest_main.uvicorn, "run", lambda *a, **k: pytest.fail("must not start"))

    assert rest_main.main() == 2
    assert rest_main.LOG_FORMAT_VARIABLE in capsys.readouterr().err


def test_the_service_starts_with_json_logs_and_without_uvicorn_log_config(tmp_path, monkeypatch):
    monkeypatch.setenv(rest_main.API_KEY_VARIABLE, "k")
    monkeypatch.setenv("TT_INGEST_DATA_DIR", str(tmp_path))
    monkeypatch.delenv(rest_main.LOG_FORMAT_VARIABLE, raising=False)
    started = {}
    monkeypatch.setattr(rest_main.uvicorn, "run", lambda app, **kwargs: started.update(kwargs))

    assert rest_main.main() == 0
    assert started["log_config"] is None
    assert logs.service_logging_active()
    assert isinstance(logging.getLogger().handlers[0].formatter, logs.JsonFormatter)
