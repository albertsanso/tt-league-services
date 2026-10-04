"""Run model: requests, stage reports and progress listeners."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from pathlib import Path
from typing import Protocol

from ingest_common.packaging import RUN_ID_PATTERN
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


class RunOutcome(Enum):
    """What an unattended caller should do next; ``RunStatus`` is kept unchanged for compatibility."""

    SUCCEEDED = "SUCCEEDED"
    NO_CHANGES = "NO_CHANGES"
    COMPLETED_WITH_ISSUES = "COMPLETED_WITH_ISSUES"
    SOURCE_UNAVAILABLE = "SOURCE_UNAVAILABLE"
    FAILED = "FAILED"


RETRYABLE_OUTCOMES = frozenset({RunOutcome.SOURCE_UNAVAILABLE})

# Marks the issue recorded when a legacy download script exits with code 1 (finished with failures).
LEGACY_FAILURE_MESSAGE = "the script finished with failures (see its log)"


FILTER_FIELDS = ("category", "group", "phase", "match_days", "gender", "territory")


@dataclass(frozen=True)
class IngestFilters:
    """One filter set; a scoped run combines several of them (each one is a scope)."""

    category: str | None = None
    group: str | None = None
    phase: str | None = None
    match_days: frozenset[int] | None = None
    gender: str | None = None
    territory: str | None = None

    @property
    def is_empty(self) -> bool:
        return not any(getattr(self, name) for name in FILTER_FIELDS)

    def describe(self) -> str:
        """``territory=Girona category=PREFERENT group=G2 days=3,4``, used in issue labels."""
        parts = [f"{name}={getattr(self, name)}" for name in ("territory", "category", "gender", "group", "phase")
                 if getattr(self, name)]
        if self.match_days:
            parts.append("days=" + ",".join(str(day) for day in sorted(self.match_days)))
        return " ".join(parts) or "all"

    def to_dict(self) -> dict[str, object]:
        return {"category": self.category, "group": self.group, "phase": self.phase, "territory": self.territory,
                "gender": self.gender, "matchDays": sorted(self.match_days) if self.match_days else None}


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
    scopes: tuple[IngestFilters, ...] = ()  # several filter sets, OR-combined; exclusive with ``filters``
    run_id: str | None = None  # written to the packaged manifest as ``runId``

    def __post_init__(self) -> None:
        if self.run_id is not None and not RUN_ID_PATTERN.fullmatch(self.run_id):
            raise ValueError("run id must be 1 to 64 characters among A-Z, a-z, 0-9, '.', '_' and '-'")
        scopes = tuple(dict.fromkeys(self.scopes))  # drop exact duplicates, keep the first-seen order
        object.__setattr__(self, "scopes", scopes)
        if not scopes:
            return
        if self.filters != IngestFilters():
            raise ValueError("use either filters or scopes, not both")
        for index, scope in enumerate(scopes, 1):
            if scope.is_empty:
                raise ValueError(f"scope {index} sets no field")
        if IngestStage.PACKAGE in self.stages and self.mode != "delta":
            raise ValueError("a scoped run packages in delta mode only (a snapshot is always the whole season)")

    def effective_scopes(self) -> tuple[IngestFilters, ...]:
        """The scopes of a scoped run, or the single filter set of any other run."""
        return self.scopes or (self.filters,)


COUNTERS = ("seen", "downloaded", "skipped_existing", "parsed", "published", "unpublished",
            "written", "unchanged", "invalid", "failed")


@dataclass
class StageReport:
    stage: IngestStage
    counters: dict[str, int] = field(default_factory=lambda: {name: 0 for name in COUNTERS})
    issues: list[tuple[str, str]] = field(default_factory=list)
    failed: bool = False  # the stage itself could not complete (configuration, contract, auth)
    skipped: str | None = None  # reason the stage was not executed
    source_unavailable: bool = False  # the federation source could not be reached (DOWNLOAD only)

    @property
    def legacy_failure(self) -> bool:
        return any(message == LEGACY_FAILURE_MESSAGE for _, message in self.issues)

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
    outcome: RunOutcome = RunOutcome.SUCCEEDED
    changes: dict[str, int] = field(default_factory=lambda: {"contentChanged": 0, "actasChanged": 0})

    @property
    def retryable(self) -> bool:
        return self.outcome in RETRYABLE_OUTCOMES

    def finish(self) -> RunStatus:
        self.finished_at = datetime.now(timezone.utc)
        has_issues = any(stage.issues or stage.counters["invalid"] or stage.counters["failed"]
                         for stage in self.stages)
        if any(stage.failed for stage in self.stages):
            self.status = RunStatus.FAILED
        elif has_issues:
            self.status = RunStatus.COMPLETED_WITH_ISSUES
        else:
            self.status = RunStatus.SUCCEEDED
        self.outcome = self._derive_outcome(has_issues)
        return self.status

    def _derive_outcome(self, has_issues: bool) -> RunOutcome:
        failed = [stage for stage in self.stages if stage.failed]
        if failed:
            unavailable = failed[0].stage is IngestStage.DOWNLOAD and failed[0].source_unavailable
            return RunOutcome.SOURCE_UNAVAILABLE if unavailable else RunOutcome.FAILED
        if any(stage.skipped for stage in self.stages) and not has_issues:
            return RunOutcome.NO_CHANGES
        return RunOutcome.COMPLETED_WITH_ISSUES if has_issues else RunOutcome.SUCCEEDED

    def to_dict(self) -> dict[str, object]:
        return {
            "source": self.request.source.value,
            "season": str(self.request.season),
            "status": self.status.value,
            "outcome": self.outcome.value,
            "retryable": self.retryable,
            "changes": dict(self.changes),
            "scopes": [scope.to_dict() for scope in self.request.effective_scopes()],
            "startedAt": self.started_at.isoformat(),
            "finishedAt": self.finished_at.isoformat() if self.finished_at else None,
            "stages": [
                {"stage": stage.stage.value, "failed": stage.failed, "skipped": stage.skipped,
                 "counters": dict(stage.counters),
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
