"""JSON output helpers."""

from __future__ import annotations

import json
import os
import tempfile
from enum import Enum
from pathlib import Path
from typing import Any


class WriteOutcome(Enum):
    WRITTEN = "WRITTEN"
    UNCHANGED = "UNCHANGED"


def render_json(payload: Any, indent: int = 2) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=indent) + "\n"


def write_text_atomically(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    handle, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    temp = Path(temp_name)
    try:
        with os.fdopen(handle, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(text)
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)


def write_json_atomically(path: Path, payload: Any, indent: int = 2) -> None:
    write_text_atomically(path, render_json(payload, indent))


def write_if_changed(path: Path, payload: Any, *, force: bool = False, indent: int = 2) -> WriteOutcome:
    text = render_json(payload, indent)
    if not force and path.is_file() and path.read_bytes() == text.encode("utf-8"):
        return WriteOutcome.UNCHANGED
    write_text_atomically(path, text)
    return WriteOutcome.WRITTEN
