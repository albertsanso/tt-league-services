"""Stage pipeline and source discovery through entry points."""

from __future__ import annotations

from importlib.metadata import entry_points
from pathlib import Path
from typing import Protocol

from ingest_common.packaging import PackagingError, package_season
from ingest_common.run import (STAGE_ORDER, IngestRequest, IngestStage, NoOpListener, ProgressListener, RunReport,
                               StageReport)
from ingest_common.settings import ConfigurationError, IngestSettings
from ingest_common.source import Source
from ingest_common.upload import PlatformUploader, UploadError

ENTRY_POINT_GROUP = "tt_league_ingest.sources"


class SourceIngestor(Protocol):
    """Implemented by each federation package and registered under ``tt_league_ingest.sources``."""

    source: Source
    supported_stages: frozenset[IngestStage]
    supported_filters: frozenset[str]  # IngestFilters fields the source understands

    def download(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport: ...

    def parse(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport: ...

    def teams(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport: ...


def discover_ingestors() -> dict[Source, SourceIngestor]:
    found: dict[Source, SourceIngestor] = {}
    for entry in entry_points(group=ENTRY_POINT_GROUP):
        ingestor = entry.load()()
        found[ingestor.source] = ingestor
    return found


def default_zip_path(request: IngestRequest, settings: IngestSettings) -> Path:
    suffix = f"-{request.mode}" if request.mode != "snapshot" else ""
    return settings.packages_dir(request.source) / f"actas-json-{request.season}{suffix}.zip"


class IngestPipeline:
    """Runs the requested stages in order and stops at the first stage that fails."""

    def __init__(self, settings: IngestSettings, ingestors: dict[Source, SourceIngestor] | None = None,
                 listener: ProgressListener | None = None) -> None:
        self._settings = settings
        self._ingestors = discover_ingestors() if ingestors is None else ingestors
        self._listener = listener or NoOpListener()

    def run(self, request: IngestRequest) -> RunReport:
        report = RunReport(request)
        ingestor = self._ingestors.get(request.source)
        if ingestor is None:
            return self._fail_early(report, f"no ingestor registered for source {request.source.value}")
        unsupported = [stage.value for stage in request.stages if stage not in ingestor.supported_stages
                       and stage not in (IngestStage.PACKAGE, IngestStage.UPLOAD)]
        if unsupported:
            return self._fail_early(report, f"{request.source.value} does not support stage(s): {', '.join(unsupported)}")
        unsupported_filters = [name for name in ("category", "group", "phase", "match_days", "gender", "territory")
                               if getattr(request.filters, name) and name not in ingestor.supported_filters]
        if unsupported_filters:
            return self._fail_early(
                report, f'{request.source.value} does not support filter(s): {', '.join(unsupported_filters)}')
        if IngestStage.UPLOAD in request.stages:
            try:
                self._settings.require_upload()
            except ConfigurationError as error:
                return self._fail_early(report, str(error))

        for stage in STAGE_ORDER:
            if stage not in request.stages:
                continue
            self._listener.stage_started(stage)
            stage_report = self._execute(stage, ingestor, request, report)
            report.stages.append(stage_report)
            self._listener.stage_finished(stage_report)
            if stage_report.failed:
                break
        report.finish()
        return report

    def _fail_early(self, report: RunReport, message: str) -> RunReport:
        stage = report.request.stages[0] if report.request.stages else IngestStage.DOWNLOAD
        failed = StageReport(stage)
        failed.fail(report.request.source.value, message)
        report.stages.append(failed)
        report.finish()
        return report

    def _execute(self, stage: IngestStage, ingestor: SourceIngestor, request: IngestRequest,
                 report: RunReport) -> StageReport:
        if stage is IngestStage.DOWNLOAD:
            return ingestor.download(request, self._settings, self._listener)
        if stage is IngestStage.PARSE:
            return ingestor.parse(request, self._settings, self._listener)
        if stage is IngestStage.TEAMS:
            return ingestor.teams(request, self._settings, self._listener)
        if stage is IngestStage.PACKAGE:
            return self._package(request, report)
        return self._upload(request, report)

    def _package(self, request: IngestRequest, report: RunReport) -> StageReport:
        stage = StageReport(IngestStage.PACKAGE)
        output = request.zip_path or default_zip_path(request, self._settings)
        actas_dir = self._settings.actas_json_dir(request.source) / str(request.season)
        teams_file = self._settings.equipos_json_dir(request.source) / f"{request.season}.json"
        try:
            result = package_season(
                source=request.source, season=request.season, actas_dir=actas_dir,
                teams_file=teams_file if request.source is Source.RFETM and teams_file.is_file() else None,
                output=output, mode=request.mode, match_days=request.filters.match_days,
                force=request.force, dry_run=request.dry_run)
        except PackagingError as error:
            stage.fail(str(output), str(error))
            return stage
        stage.count("written", result.actas + result.teams)
        report.outputs["package"] = str(result.zip_path)
        return stage

    def _upload(self, request: IngestRequest, report: RunReport) -> StageReport:
        stage = StageReport(IngestStage.UPLOAD)
        zip_path = request.zip_path or (Path(report.outputs["package"]) if "package" in report.outputs
                                        else default_zip_path(request, self._settings))
        try:
            api_url, token = self._settings.require_upload()
            PlatformUploader(api_url, token).upload(zip_path, allow_published_shrink=request.allow_published_shrink)
        except (ConfigurationError, UploadError) as error:
            stage.fail(str(zip_path), str(error))
            return stage
        stage.count("written")
        report.outputs["upload"] = str(zip_path)
        return stage
