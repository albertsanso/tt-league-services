"""Helpers shared by the ingestors to turn legacy script results into stage reports."""

from __future__ import annotations

import json
from collections.abc import Callable
from pathlib import Path

from ingest_common.match_day_status import ScanResult, write_status_report
from ingest_common.run import LEGACY_FAILURE_MESSAGE, IngestStage, StageReport
from ingest_common.source import Source
from ingest_common.validation import ActaValidator


def match_days_argument(days: frozenset[int] | None) -> str | None:
    """``frozenset({1, 4})`` -> ``"1,4"`` as accepted by the legacy ``--match_day`` options."""
    return ",".join(str(day) for day in sorted(days)) if days else None


def record_exit_code(report: StageReport, script: str, code: int) -> bool:
    """Map a legacy script exit code: 0 ok, 1 completed with failures, anything else fails the stage.

    Returns whether the script reported failures (exit code 1); the issue carries ``LEGACY_FAILURE_MESSAGE`` so the
    pipeline can tell a failing download apart without reading each script's metrics file.
    """
    if code == 0:
        return False
    if code == 1:
        report.issue(script, LEGACY_FAILURE_MESSAGE)
        return True
    report.fail(script, f"the script failed with exit code {code}")
    return False


def count_files(report: StageReport, directory: Path, patterns: tuple[str, ...]) -> None:
    """Count downloaded files under ``directory`` as ``seen``."""
    if directory.is_dir():
        report.count("seen", sum(1 for pattern in patterns for _ in directory.rglob(pattern)))


def scan_actas(report: StageReport, season_dir: Path, validator: ActaValidator | None = None) -> None:
    """Count produced actas by publication state and, with a validator, record invalid ones."""
    if not season_dir.is_dir():
        return
    for path in sorted(season_dir.rglob("*.json")):
        report.count("parsed")
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            report.count("invalid")
            report.issue(str(path), f"unreadable JSON: {error}")
            continue
        if isinstance(payload, dict) and payload.get("acta_publicada") is False:
            report.count("unpublished")
        else:
            report.count("published")
        if validator is not None:
            errors = validator.errors(payload)
            if errors:
                report.count("invalid")
                report.issue(str(path), "; ".join(errors[:3]))


def write_match_day_status(report: StageReport, content_dir: Path, source: Source,
                           scan: Callable[[Path], ScanResult]) -> Path:
    """Rebuild ``match-days-status.json`` from the saved pages, whatever the download outcome was."""
    result = scan(content_dir)
    for where, message in result.issues:
        report.issue(where, f"unreadable page left out of the match-day status: {message}")
    return write_status_report(content_dir, source, result)


def new_report(stage: IngestStage) -> StageReport:
    return StageReport(stage)
