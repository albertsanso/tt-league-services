"""BCNESA ingestor: wires the ported legacy scripts to the ingestion pipeline."""

from __future__ import annotations

from pathlib import PurePosixPath

from ingest_bcnesa import download, parse, status
from ingest_common.run import IngestFilters, IngestRequest, IngestStage, ProgressListener, StageReport
from ingest_common.scan import count_files, match_days_argument, run_per_scope, scan_actas, write_match_day_status
from ingest_common.settings import IngestSettings
from ingest_common.source import Source
from ingest_common.validation import ActaValidator


DOWNLOAD_DELAY_SECONDS = 1.0  # the downloader's own --delay default
# Category folders start with the territory's representation code (``rtb-preferent``, ``RTB PREFERENT``).
TERRITORY_PREFIXES = {"barcelona": "rtb", "girona": "rtg", "lleida": "rtl", "tarragona": "rtt"}


def _filter_args(request: IngestRequest, filters: IngestFilters) -> list[str]:
    args: list[str] = ["--season", str(request.season)]
    for option, value in (("--category", filters.category), ("--group", filters.group), ("--phase", filters.phase),
                          ("--match_day", match_days_argument(filters.match_days))):
        if value:
            args += [option, value]
    if request.force:
        args.append("--force")
    return args


class BcnesaIngestor:
    source = Source.BCNESA
    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE, IngestStage.PACKAGE, IngestStage.UPLOAD})
    supported_filters = frozenset({"category", "group", "phase", "match_days", "territory"})

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
                args += ["--delay", str(request.delay_seconds)]
            calls.append((scope, args))
        delay = DOWNLOAD_DELAY_SECONDS if request.delay_seconds is None else request.delay_seconds
        run_per_scope(report, "bcnesa download", calls, download.main, delay)
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
        run_per_scope(report, "bcnesa parse", calls, parse.main)
        scan_actas(report, settings.actas_json_dir(self.source) / str(request.season), ActaValidator())
        return report

    def teams(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        raise NotImplementedError("BCNESA has no teams stage")

    def scope_matches(self, scope: IngestFilters, relative: PurePosixPath, match_day: int | None) -> bool:
        """``<category>/<group>/<phase>/jornada_*.json``, compared the way the parser compares its filters.

        The territory is read from the category folder prefix (``rtb``, ``rtg``, ``rtl``, ``rtt``); a folder
        without a known prefix is matched on its other fields only.
        """
        if len(relative.parts) != 4:
            return False
        category, group, phase, _ = relative.parts
        if not parse.category_matches(category, scope.category):
            return False
        if scope.group and parse.normalise_group(group) != parse.normalise_group(scope.group):
            return False
        if scope.phase and parse.fold(phase) != parse.fold(scope.phase):
            return False
        if scope.match_days and match_day not in scope.match_days:
            return False
        if scope.territory:
            prefix = parse.fold(category)[:3]
            if prefix in TERRITORY_PREFIXES.values() and prefix != TERRITORY_PREFIXES.get(parse.fold(scope.territory)):
                return False
        return True
