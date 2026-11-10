"""Logging of the download and parse scripts.

Standalone, a script logs to its file and to stderr through handlers of its own logger (it never touches the root
logger). While the REST service logs (``ingest_common.logs``) it only attaches its own file handler and lets the
records propagate to the service's root JSON handler.
"""

from __future__ import annotations

import logging
import sys
from pathlib import Path

from ingest_common import logs

FORMAT = "%(asctime)s %(levelname)s %(message)s"


def configure_script_logger(name: str, log_file: Path, level: int = logging.INFO) -> logging.Logger:
    logger = logging.getLogger(name)
    if logs.service_logging_active():
        logs.attach_script_file_handler(logger, log_file, FORMAT, level)
        return logger
    log_file.parent.mkdir(parents=True, exist_ok=True)
    formatter = logging.Formatter(FORMAT)
    file_handler = logging.FileHandler(log_file, encoding="utf-8")
    console = logging.StreamHandler(sys.stderr)
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(errors="replace")
    for handler in (file_handler, console):
        handler.setLevel(level)
        handler.setFormatter(formatter)
    for existing in list(logger.handlers):  # an earlier call of the same process
        logger.removeHandler(existing)
        existing.close()
    logger.addHandler(file_handler)
    logger.addHandler(console)
    logger.setLevel(level)
    logger.propagate = False
    return logger
