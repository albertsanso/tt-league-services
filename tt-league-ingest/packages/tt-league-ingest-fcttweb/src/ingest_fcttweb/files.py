"""Atomic file writes shared by the downloader and the parser."""

from __future__ import annotations

import os
from pathlib import Path


def atomic_write(path: Path, data: bytes) -> None:
    """Write ``data`` through a temporary sibling file so a reader never sees a half-written file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    try:
        temporary.write_bytes(data)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)
