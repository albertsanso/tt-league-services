"""FastAPI application: triggers ingestion runs and reports their progress."""

from __future__ import annotations

import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel

from ingest_common.match_days import parse_match_days
from ingest_common.pipeline import IngestPipeline, SourceIngestor, discover_ingestors
from ingest_common.run import (IngestFilters, IngestRequest, IngestStage, RunReport, RunStatus, StageReport)
from ingest_common.season import Season
from ingest_common.settings import IngestSettings
from ingest_common.source import Source

HISTORY_LIMIT = 50


class FiltersBody(BaseModel):
    category: str | None = None
    group: str | None = None
    phase: str | None = None
    matchDays: str | None = None
    gender: str | None = None
    territory: str | None = None


class RunBody(BaseModel):
    source: str
    season: str | None = None
    stages: list[str]
    filters: FiltersBody = FiltersBody()
    force: bool = False
    mode: str = "snapshot"
    allowPublishedShrink: bool = False


@dataclass
class RunRecord:
    run_id: str
    request: IngestRequest
    status: str = "QUEUED"
    current_stage: str | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    stages: list[StageReport] = field(default_factory=list)
    report: RunReport | None = None
    error: str | None = None

    def to_dict(self) -> dict[str, Any]:
        stages = self.report.stages if self.report else self.stages
        return {
            "runId": self.run_id,
            "source": self.request.source.value,
            "season": str(self.request.season),
            "status": self.status,
            "currentStage": self.current_stage,
            "createdAt": self.created_at.isoformat(),
            "finishedAt": self.report.finished_at.isoformat() if self.report and self.report.finished_at else None,
            "stages": [{"stage": s.stage.value, "counters": dict(s.counters),
                        "issues": [{"where": w, "message": m} for w, m in s.issues]} for s in stages],
            "package": self.report.outputs.get("package") if self.report else None,
            "error": self.error,
        }


class RunRegistry:
    """In-memory run history (not persisted across restarts)."""

    def __init__(self) -> None:
        self._runs: dict[str, RunRecord] = {}
        self._lock = threading.Lock()

    def add(self, request: IngestRequest) -> RunRecord:
        record = RunRecord(uuid.uuid4().hex, request)
        with self._lock:
            self._runs[record.run_id] = record
        return record

    def get(self, run_id: str) -> RunRecord | None:
        with self._lock:
            return self._runs.get(run_id)

    def recent(self, limit: int = HISTORY_LIMIT) -> list[RunRecord]:
        with self._lock:
            return sorted(self._runs.values(), key=lambda r: r.created_at, reverse=True)[:limit]

    def active_for(self, source: Source) -> RunRecord | None:
        with self._lock:
            return next((r for r in self._runs.values()
                         if r.request.source is source and r.status in ("QUEUED", "RUNNING")), None)


class _RecordListener:
    def __init__(self, record: RunRecord) -> None:
        self._record = record

    def stage_started(self, stage: IngestStage) -> None:
        self._record.current_stage = stage.value

    def item_processed(self, stage: IngestStage, item: str) -> None:
        pass

    def stage_finished(self, report: StageReport) -> None:
        self._record.stages.append(report)


def create_app(settings: IngestSettings, api_key: str, ingestors: dict[Source, SourceIngestor] | None = None) -> FastAPI:
    if not api_key:
        raise ValueError("TT_INGEST_REST_API_KEY is required")
    app = FastAPI(title="tt-league-ingest")
    registry = RunRegistry()
    executor = ThreadPoolExecutor(max_workers=1)  # never scrape federation sites in parallel
    available = discover_ingestors() if ingestors is None else ingestors
    app.state.registry = registry
    app.state.executor = executor

    def require_key(x_api_key: str | None = Header(default=None)) -> None:
        if x_api_key != api_key:
            raise HTTPException(status_code=401, detail="invalid or missing X-API-Key")

    def execute(record: RunRecord) -> None:
        record.status = "RUNNING"
        try:
            report = IngestPipeline(settings, available, _RecordListener(record)).run(record.request)
        except Exception as error:  # noqa: BLE001 - the failure is recorded on the run, not swallowed
            record.status = RunStatus.FAILED.value
            record.error = f"{type(error).__name__}: {error}"
            return
        record.report = report
        record.current_stage = None
        record.status = report.status.value

    def to_request(body: RunBody) -> IngestRequest:
        try:
            source = Source.parse(body.source)
            season = Season.parse(body.season) if body.season else Season.current()
            stages = tuple(IngestStage(stage.upper()) for stage in body.stages)
            if not stages:
                raise ValueError("stages must not be empty")
            filters = IngestFilters(body.filters.category, body.filters.group, body.filters.phase,
                                    parse_match_days(body.filters.matchDays) if body.filters.matchDays else None,
                                    body.filters.gender, body.filters.territory)
            if body.mode not in ("snapshot", "delta"):
                raise ValueError("mode must be snapshot or delta")
            if body.mode == "delta" and not filters.match_days:
                raise ValueError("mode delta requires filters.matchDays")
            return IngestRequest(source, season, stages, filters, body.force, None, body.mode,
                                 body.allowPublishedShrink)
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.post("/api/v1/ingest/runs", status_code=202, dependencies=[Depends(require_key)])
    def create_run(body: RunBody) -> dict[str, str]:
        request = to_request(body)
        if registry.active_for(request.source) is not None:
            raise HTTPException(status_code=409, detail=f"a run for {request.source.value} is already active")
        record = registry.add(request)
        executor.submit(execute, record)
        return {"runId": record.run_id}

    @app.get("/api/v1/ingest/runs", dependencies=[Depends(require_key)])
    def list_runs(limit: int = HISTORY_LIMIT) -> list[dict[str, Any]]:
        return [record.to_dict() for record in registry.recent(max(1, min(limit, 200)))]

    @app.get("/api/v1/ingest/runs/{run_id}", dependencies=[Depends(require_key)])
    def get_run(run_id: str) -> dict[str, Any]:
        record = registry.get(run_id)
        if record is None:
            raise HTTPException(status_code=404, detail="unknown run")
        return record.to_dict()

    return app
