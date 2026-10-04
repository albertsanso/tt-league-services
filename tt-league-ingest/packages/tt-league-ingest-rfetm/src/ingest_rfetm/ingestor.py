"""RFETM ingestor: wires the ported legacy scripts to the ingestion pipeline."""

from __future__ import annotations

import json
from pathlib import PurePosixPath

from ingest_common.run import IngestFilters, IngestRequest, IngestStage, ProgressListener, StageReport
from ingest_common.scan import count_files, record_exit_code, run_per_scope, scan_actas, write_match_day_status
from ingest_common.settings import IngestSettings
from ingest_common.source import Source
from ingest_common.validation import ActaValidator, TeamsValidator
from ingest_rfetm import download, parse, status, teams_download, teams_parse


def _jornada_args(request: IngestRequest, filters: IngestFilters) -> list[str]:
    args: list[str] = []
    for day in sorted(filters.match_days or ()):
        args += ["--jornada", str(day)]
    if filters.category:
        args += ["--category", filters.category]
    if request.force:
        args.append("--force")
    return args


class RfetmIngestor:
    source = Source.RFETM
    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE, IngestStage.TEAMS, IngestStage.PACKAGE,
                                  IngestStage.UPLOAD})
    supported_filters = frozenset({"category", "match_days"})

    def download(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        report = StageReport(IngestStage.DOWNLOAD)
        calls = []
        for scope in request.effective_scopes():
            args = ["--content-dir", str(settings.content_dir(self.source)), "--season", str(request.season)]
            args += _jornada_args(request, scope)
            if request.delay_seconds is not None:
                args += ["--delay", str(request.delay_seconds)]
            calls.append((scope, args))
        delay = download.DEFAULT_DELAY if request.delay_seconds is None else request.delay_seconds
        run_per_scope(report, "rfetm download", calls, download.main, delay)
        count_files(report, settings.content_dir(self.source) / str(request.season), ("*.html", "*.pdf"))
        write_match_day_status(report, settings.content_dir(self.source), self.source, status.scan)
        return report

    def parse(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        report = StageReport(IngestStage.PARSE)
        calls = [(scope, ["--content-dir", str(settings.content_dir(self.source)),
                          "--json-dir", str(settings.actas_json_dir(self.source)),
                          "--season", str(request.season), "--validate"] + _jornada_args(request, scope))
                 for scope in request.effective_scopes()]
        run_per_scope(report, "rfetm parse", calls, parse.main)
        scan_actas(report, settings.actas_json_dir(self.source) / str(request.season), ActaValidator())
        return report

    def teams(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        report = StageReport(IngestStage.TEAMS)
        html_dir = settings.content_dir(self.source) / "equipos"
        json_dir = settings.equipos_json_dir(self.source)
        season = str(request.season)
        download_args = ["--output-dir", str(html_dir), "--start-season", season]
        if request.force:
            download_args.append("--overwrite")
        record_exit_code(report, "rfetm teams download", teams_download.main(download_args))
        if report.failed:
            return report
        parse_args = ["--input-dir", str(html_dir), "--output-file", str(json_dir / "{season}.json"),
                      "--season", season, "--overwrite"]
        record_exit_code(report, "rfetm teams parse", teams_parse.main(parse_args))
        produced = json_dir / f"{season}.json"
        if produced.is_file():
            errors = TeamsValidator().errors(json.loads(produced.read_text(encoding="utf-8")))
            report.count("written")
            if errors:
                report.count("invalid")
                report.issue(str(produced), "; ".join(errors[:3]))
        else:
            report.fail(str(produced), "the teams file was not produced")
        return report

    def scope_matches(self, scope: IngestFilters, relative: PurePosixPath, match_day: int | None) -> bool:
        """``<category>/<day>/<sex>/acta_*.json``: the category folder as the parser compares it, and the day."""
        if len(relative.parts) != 4:
            return False
        if scope.category and relative.parts[0] != scope.category:
            return False
        return not scope.match_days or match_day in scope.match_days
