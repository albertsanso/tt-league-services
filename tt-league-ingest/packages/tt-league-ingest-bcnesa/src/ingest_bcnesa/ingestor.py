"""BCNESA ingestor: wires the ported legacy scripts to the ingestion pipeline."""

from __future__ import annotations

from ingest_bcnesa import download, parse, status
from ingest_common.run import IngestRequest, IngestStage, ProgressListener, StageReport
from ingest_common.scan import (count_files, match_days_argument, record_exit_code, scan_actas,
                                 write_match_day_status)
from ingest_common.settings import IngestSettings
from ingest_common.source import Source
from ingest_common.validation import ActaValidator


def _filter_args(request: IngestRequest) -> list[str]:
    filters = request.filters
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
        args = _filter_args(request) + [
            "--output_dir", str(settings.content_dir(self.source)),
            "--log_file", str(logs / "download.log"),
            "--metrics_file", str(logs / "download_metrics.json")]
        if request.filters.territory:  # only the downloader filters by territory
            args += ["--territory", request.filters.territory]
        if request.delay_seconds is not None:
            args += ["--delay", str(request.delay_seconds)]
        record_exit_code(report, "bcnesa download", download.main(args))
        count_files(report, settings.content_dir(self.source) / str(request.season), ("*.html",))
        write_match_day_status(report, settings.content_dir(self.source), self.source, status.scan)
        return report

    def parse(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        report = StageReport(IngestStage.PARSE)
        logs = settings.logs_dir(self.source)
        args = _filter_args(request) + [
            "--input_dir", str(settings.content_dir(self.source)),
            "--output_dir", str(settings.actas_json_dir(self.source)),
            "--log_file", str(logs / "parse.log")]
        record_exit_code(report, "bcnesa parse", parse.main(args))
        scan_actas(report, settings.actas_json_dir(self.source) / str(request.season), ActaValidator())
        return report

    def teams(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        raise NotImplementedError("BCNESA has no teams stage")
