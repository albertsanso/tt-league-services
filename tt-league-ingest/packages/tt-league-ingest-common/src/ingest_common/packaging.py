"""Upload ZIP packaging: manifest contract and season packages."""

from __future__ import annotations

import hashlib
import json
import os
import re
import tempfile
import zipfile
from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass
from fnmatch import fnmatchcase
from importlib.metadata import version
from pathlib import Path, PurePosixPath
from typing import Any

from ingest_common.fingerprint import file_digest
from ingest_common.season import Season
from ingest_common.source import Source

MANIFEST_NAME = "manifest.json"
ACTAS_PREFIX = "actas-json"
TEAMS_PREFIX = "equipos-json"
MODES = ("snapshot", "delta")
GENERATOR = "tt-league-ingest"
RUN_ID_PATTERN = re.compile(r"[A-Za-z0-9._-]{1,64}")
PROVENANCE_KEYS = ("runId", "generator", "generatorVersion", "contentSha256", "matchCounts")


class PackagingError(Exception):
    """The package cannot be built (empty selection, existing output, bad input)."""


def build_manifest(source: Source, seasons: list[Season], assets: dict[str, list[str]],
                   mode: str | None = None, provenance: dict[str, Any] | None = None) -> dict[str, Any]:
    """Manifest following ``ResourceZipService``: ``source``, ``seasons``, ``assets``, optional ``mode`` and the
    optional provenance keys (``runId``, ``generator``, ``generatorVersion``, ``contentSha256``, ``matchCounts``)."""
    if mode is not None and mode not in MODES:
        raise PackagingError(f"mode must be one of {MODES}, got '{mode}'")
    unknown = set(assets) - {"ACTAS", "TEAMS"}
    if unknown:
        raise PackagingError(f"unknown asset(s): {sorted(unknown)}")
    unknown_provenance = set(provenance or {}) - set(PROVENANCE_KEYS)
    if unknown_provenance:
        raise PackagingError(f"unknown provenance key(s): {sorted(unknown_provenance)}")
    manifest: dict[str, Any] = {
        "source": source.value,
        "seasons": [str(season) for season in seasons],
        "assets": {name: {"files": sorted(files)} for name, files in assets.items()},
    }
    if mode is not None:
        manifest["mode"] = mode
    for key in PROVENANCE_KEYS:
        if provenance and key in provenance:
            manifest[key] = provenance[key]
    return manifest


def content_sha256(entries: list[tuple[Path, str]]) -> str:
    """The manifest ``contentSha256`` of the ZIP entries ``(file, name)`` other than ``manifest.json``.

    Names are sorted (code-point order, which is UTF-8 byte order) and ``<name>\\n<sha256 of the file>\\n`` is
    appended for each one; the result is the SHA-256 of that buffer. ``ResourceZipService`` recomputes it with
    the same algorithm and rejects a mismatch.
    """
    duplicates = sorted(name for name, count in Counter(name for _, name in entries).items() if count > 1)
    if duplicates:
        raise PackagingError(f"duplicate ZIP entr(y/ies): {duplicates}")
    digest = hashlib.sha256()
    for path, name in sorted(entries, key=lambda item: item[1]):
        if name == MANIFEST_NAME:
            continue
        digest.update(f"{name}\n{file_digest(path)}\n".encode("utf-8"))
    return digest.hexdigest()


def match_counts(paths: list[Path]) -> dict[str, int]:
    """``expected``/``withResult``/``pending`` over packaged actas; a missing ``acta_publicada`` means published."""
    pending = 0
    for path in paths:
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            raise PackagingError(f"cannot read acta {path}: {error}") from error
        if not isinstance(payload, dict):
            raise PackagingError(f"acta {path} is not a JSON object")
        published = payload.get("acta_publicada", True)
        if not isinstance(published, bool):
            raise PackagingError(f"acta {path} has a non-boolean acta_publicada")
        pending += not published
    return {"expected": len(paths), "withResult": len(paths) - pending, "pending": pending}


def _patterns_match(relative: PurePosixPath, patterns: tuple[str, ...]) -> bool:
    candidates = [relative.as_posix(), relative.name]
    for parent in relative.parents:
        if parent.as_posix() != ".":
            candidates += [parent.as_posix(), parent.name]
    return any(fnmatchcase(candidate, pattern) for pattern in patterns for candidate in candidates)


def _payload_match_day(path: Path) -> int | None:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    day = payload.get("jornada") if isinstance(payload, dict) else None
    if isinstance(day, int) and not isinstance(day, bool):
        return day
    if isinstance(day, str) and day.strip().isdigit():
        return int(day.strip())
    return None


@dataclass(frozen=True)
class PackageResult:
    zip_path: Path
    manifest: dict[str, Any]
    actas: int
    teams: int
    written: bool  # False for a dry run


def package_season(*, source: Source, season: Season, actas_dir: Path | None, teams_file: Path | None,
                   output: Path, mode: str = "snapshot", match_days: frozenset[int] | None = None,
                   select: Callable[[PurePosixPath, int | None], bool] | None = None,
                   include: tuple[str, ...] = (), exclude: tuple[str, ...] = (),
                   force: bool = False, dry_run: bool = False, run_id: str | None = None) -> PackageResult:
    """Build the upload ZIP for one season.

    ``actas_dir`` is the season folder (``.../actas-json/<season>``); entries are stored under
    ``actas-json/<season>/...``. A delta keeps only actas whose payload ``jornada`` is in
    ``match_days`` (or, when ``select`` is given, the actas ``select(relative path, jornada)`` accepts)
    and fails when nothing is selected. A snapshot ignores both.

    The manifest always carries ``generator``, ``generatorVersion`` and ``contentSha256``. It also carries
    ``matchCounts`` when actas are packaged and ``runId`` when ``run_id`` is given.
    """
    if run_id is not None and not RUN_ID_PATTERN.fullmatch(run_id):
        raise PackagingError(f"run id must be 1 to 64 characters among A-Z, a-z, 0-9, '.', '_' and '-', got '{run_id}'")
    if mode not in MODES:
        raise PackagingError(f"mode must be one of {MODES}, got '{mode}'")
    if mode == "delta" and not match_days and select is None:
        raise PackagingError("delta mode requires at least one match day")
    if output.exists() and not force and not dry_run:
        raise PackagingError(f"output already exists: {output} (use force to overwrite)")

    entries: list[tuple[Path, str]] = []
    if actas_dir is not None:
        if not actas_dir.is_dir():
            raise PackagingError(f"actas folder not found: {actas_dir}")
        for path in sorted(actas_dir.rglob("*.json")):
            relative = PurePosixPath(path.relative_to(actas_dir).as_posix())
            if relative.name == MANIFEST_NAME or (_patterns_match(relative, exclude)
                                                   and not _patterns_match(relative, include)):
                continue
            if mode == "delta":
                day = _payload_match_day(path)
                if not (select(relative, day) if select is not None else day in match_days):
                    continue
            entries.append((path, f"{ACTAS_PREFIX}/{season}/{relative.as_posix()}"))
        if not entries:
            raise PackagingError(f"no actas selected in {actas_dir}")
    actas_names = [name for _, name in entries]
    actas_paths = [path for path, _ in entries]

    team_names: list[str] = []
    if teams_file is not None:
        if not teams_file.is_file():
            raise PackagingError(f"teams file not found: {teams_file}")
        team_names = [f"{TEAMS_PREFIX}/{teams_file.name}"]
        entries.append((teams_file, team_names[0]))
    if not entries:
        raise PackagingError("nothing to package")

    assets: dict[str, list[str]] = {}
    if actas_names:
        assets["ACTAS"] = actas_names
    if team_names:
        assets["TEAMS"] = team_names
    provenance: dict[str, Any] = {"generator": GENERATOR, "generatorVersion": version("tt-league-ingest-common"),
                                  "contentSha256": content_sha256(entries)}
    if actas_paths:
        provenance["matchCounts"] = match_counts(actas_paths)
    if run_id is not None:
        provenance["runId"] = run_id
    manifest = build_manifest(source, [season], assets, mode, provenance)
    if not dry_run:
        _write_zip(output, manifest, entries)
    return PackageResult(output, manifest, len(actas_names), len(team_names), not dry_run)


def _write_zip(target: Path, manifest: dict[str, Any], entries: list[tuple[Path, str]]) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    handle, temp_name = tempfile.mkstemp(prefix=f".{target.name}.", suffix=".tmp", dir=target.parent)
    os.close(handle)
    temp = Path(temp_name)
    try:
        with zipfile.ZipFile(temp, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr(MANIFEST_NAME, (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
            for path, name in sorted(entries, key=lambda item: item[1]):
                archive.write(path, name)
        os.replace(temp, target)
    finally:
        temp.unlink(missing_ok=True)
