"""Run model: requests, stage reports and progress listeners."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from pathlib import Path
from typing import Protocol

from ingest_common.season import Season
from ingest_common.source import Source


class IngestStage(Enum):
    DOWNLOAD = "DOWNLOAD"
    PARSE = "PARSE"
    TEAMS = "TEAMS"
    PACKAGE = "PACKAGE"
    UPLOAD = "UPLOAD"


STAGE_ORDER = (IngestStage.DOWNLOAD, IngestStage.PARSE, IngestStage.TEAMS, IngestStage.PACKAGE, IngestStage.UPLOAD)


class RunStatus(Enum):
    SUCCEEDED = "SUCCEEDED"
    COMPLETED_WITH_ISSUES = "COMPLETED_WITH_ISSUES"
    FAILED = "FAILED"


@dataclass(frozen=True)
class IngestFilters:
    category: str | None = None
    group: str | None = None
    phase: str | None = None
    match_days: frozenset[int] | None = None
    gender: str | None = None
    territory: str | None = None


@dataclass(frozen=True)
class IngestRequest:
    source: Source
    season: Season
    stages: tuple[IngestStage, ...]
    filters: IngestFilters = IngestFilters()
    force: bool = False
    delay_seconds: float | None = None
    mode: str = "snapshot"  # snapshot | delta, used by the PACKAGE stage
    allow_published_shrink: bool = False
    zip_path: Path | None = None  # PACKAGE output / UPLOAD input override
    dry_run: bool = False


COUNTERS = ("seen", "downloaded", "skipped_existing", "parsed", "published", "unpublished",
            "written", "unchanged", "invalid", "failed")


@dataclass
class StageReport:
    stage: IngestStage
    counters: dict[str, int] = field(default_factory=lambda: {name: 0 for name in COUNTERS})
    issues: list[tuple[str, str]] = field(default_factory=list)
    failed: bool = False  # the stage itself could not complete (configuration, contract, auth)

    def count(self, name: str, amount: int = 1) -> None:
        if name not in self.counters:
            raise KeyError(f"unknown counter '{name}'")
        self.counters[name] += amount

    def issue(self, where: str, message: str) -> None:
        self.issues.append((where, message))

    def fail(self, where: str, message: str) -> None:
        self.failed = True
        self.issue(where, message)


@dataclass
class RunReport:
    request: IngestRequest
    started_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    finished_at: datetime | None = None
    stages: list[StageReport] = field(default_factory=list)
    outputs: dict[str, str] = field(default_factory=dict)
    status: RunStatus = RunStatus.SUCCEEDED

    def finish(self) -> RunStatus:
        self.finished_at = datetime.now(timezone.utc)
        if any(stage.failed for stage in self.stages):
            self.status = RunStatus.FAILED
        elif any(stage.issues or stage.counters["invalid"] or stage.counters["failed"] for stage in self.stages):
            self.status = RunStatus.COMPLETED_WITH_ISSUES
        else:
            self.status = RunStatus.SUCCEEDED
        return self.status

    def to_dict(self) -> dict[str, object]:
        return {
            "source": self.request.source.value,
            "season": str(self.request.season),
            "status": self.status.value,
            "startedAt": self.started_at.isoformat(),
            "finishedAt": self.finished_at.isoformat() if self.finished_at else None,
            "stages": [
                {"stage": stage.stage.value, "failed": stage.failed, "counters": dict(stage.counters),
                 "issues": [{"where": where, "message": message} for where, message in stage.issues]}
                for stage in self.stages
            ],
            "outputs": dict(self.outputs),
        }


class ProgressListener(Protocol):
    def stage_started(self, stage: IngestStage) -> None: ...

    def item_processed(self, stage: IngestStage, item: str) -> None: ...

    def stage_finished(self, report: StageReport) -> None: ...


class NoOpListener:
    def stage_started(self, stage: IngestStage) -> None:
        pass

    def item_processed(self, stage: IngestStage, item: str) -> None:
        pass

    def stage_finished(self, report: StageReport) -> None:
        pass
