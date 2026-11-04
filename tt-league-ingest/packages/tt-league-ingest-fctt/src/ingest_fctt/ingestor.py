"""FCTT ingestor: wires the ported legacy scripts to the ingestion pipeline."""

from __future__ import annotations

from pathlib import PurePosixPath

from ingest_common.run import IngestFilters, IngestRequest, IngestStage, ProgressListener, StageReport
from ingest_common.scan import count_files, match_days_argument, run_per_scope, scan_actas, write_match_day_status
from ingest_common.settings import IngestSettings
from ingest_common.source import Source
from ingest_common.validation import ActaValidator
from ingest_fctt import download, parse, status


DOWNLOAD_DELAY_SECONDS = 3.0  # the downloader's own --request_delay default


def _category_argument(filters: IngestFilters) -> str:
    """Category and gender go together in the scripts' ``--category`` option (either one selects a league)."""
    return ",".join(value for value in (filters.category, filters.gender) if value)


def _filter_args(request: IngestRequest, filters: IngestFilters) -> list[str]:
    args: list[str] = ["--season", str(request.season)]
    category = _category_argument(filters)
    for option, value in (("--category", category), ("--group", filters.group), ("--phase", filters.phase),
                          ("--match_day", match_days_argument(filters.match_days))):
        if value:
            args += [option, value]
    if request.force:
        args.append("--force")
    return args


class FcttIngestor:
    source = Source.FCTT
    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE, IngestStage.PACKAGE, IngestStage.UPLOAD})
    supported_filters = frozenset({"category", "group", "phase", "match_days", "gender", "territory"})

    def download(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        report = StageReport(IngestStage.DOWNLOAD)
        logs = settings.logs_dir(self.source)
        calls = []
        for scope in request.effective_scopes():
            args = _filter_args(request, scope) + [
                "--output_dir", str(settings.content_dir(self.source)),
                "--log_file", str(logs / "download.log"),
                "--metrics_file", str(logs / "download_metrics.json")]
            if scope.territory:  # only the downloader filters by territory
                args += ["--territory", scope.territory]
            if request.delay_seconds is not None:
                args += ["--request_delay", str(request.delay_seconds)]
            calls.append((scope, args))
        delay = DOWNLOAD_DELAY_SECONDS if request.delay_seconds is None else request.delay_seconds
        run_per_scope(report, "fctt download", calls, download.main, delay)
        count_files(report, settings.content_dir(self.source) / str(request.season), ("*.html",))
        write_match_day_status(report, settings.content_dir(self.source), self.source, status.scan)
        return report

    def parse(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        report = StageReport(IngestStage.PARSE)
        logs = settings.logs_dir(self.source)
        calls = [(scope, _filter_args(request, scope) + [
                     "--input_dir", str(settings.content_dir(self.source)),
                     "--output_dir", str(settings.actas_json_dir(self.source)),
                     "--log_file", str(logs / "parse.log")])
                 for scope in request.effective_scopes()]
        run_per_scope(report, "fctt parse", calls, parse.main)
        scan_actas(report, settings.actas_json_dir(self.source) / str(request.season), ActaValidator())
        return report

    def teams(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        raise NotImplementedError("FCTT has no teams stage")

    def scope_matches(self, scope: IngestFilters, relative: PurePosixPath, match_day: int | None) -> bool:
        """``<category>/<group>/<phase>/jornada_*.json`` with the scripts' slug filters.

        As in the downloader, the category filter selects a league by its category or its gender (female for the
        ``femenina`` categories, as the parser decides). Territory is accepted and ignored: FCTT has one territory.
        """
        if len(relative.parts) != 4:
            return False
        category, group, phase = (download.slugify(part) for part in relative.parts[:3])
        categories = download.parse_filter(_category_argument(scope))
        gender = "female" if "femen" in category else "male"
        if categories is not None and category not in categories and gender not in categories:
            return False
        groups, phases = download.parse_filter(scope.group), download.parse_filter(scope.phase)
        if (groups is not None and group not in groups) or (phases is not None and phase not in phases):
            return False
        return not scope.match_days or match_day in scope.match_days
