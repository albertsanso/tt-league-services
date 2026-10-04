"""Cheap folder fingerprints used to tell whether a run changed anything."""

from __future__ import annotations

import hashlib
from collections.abc import Mapping
from pathlib import Path

CHUNK_SIZE = 1024 * 1024


def content_fingerprint(directory: Path, patterns: tuple[str, ...]) -> dict[str, tuple[int, int]]:
    """Relative POSIX path -> ``(size, mtime_ns)`` for the downloaded pages and PDFs under ``directory``.

    Size and modification time are used instead of a hash so large PDFs are not read on every run.
    """
    if not directory.is_dir():
        return {}
    found: dict[str, tuple[int, int]] = {}
    for pattern in patterns:
        for path in directory.rglob(pattern):
            if path.is_file():
                stat = path.stat()
                found[path.relative_to(directory).as_posix()] = (stat.st_size, stat.st_mtime_ns)
    return found


def file_digest(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(CHUNK_SIZE), b""):
            digest.update(chunk)
    return digest.hexdigest()


def json_fingerprint(directory: Path) -> dict[str, str]:
    """Relative POSIX path -> SHA-256 of the bytes for every ``*.json`` under ``directory``."""
    if not directory.is_dir():
        return {}
    return {path.relative_to(directory).as_posix(): file_digest(path)
            for path in directory.rglob("*.json") if path.is_file()}


def count_changes(before: Mapping[str, object], after: Mapping[str, object]) -> int:
    """Added + modified + removed entries between two fingerprints."""
    added_or_modified = sum(1 for key, value in after.items() if key not in before or before[key] != value)
    removed = sum(1 for key in before if key not in after)
    return added_or_modified + removed
