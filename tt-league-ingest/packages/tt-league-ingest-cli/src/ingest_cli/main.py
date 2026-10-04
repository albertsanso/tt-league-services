"""``tt-league-ingest`` command line interface."""

from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Sequence
from pathlib import Path

from ingest_common.match_days import parse_match_days
from ingest_common.packaging import MODES
from ingest_common.pipeline import IngestPipeline, SourceIngestor
from ingest_common.run import (IngestFilters, IngestRequest, IngestStage, ProgressListener, RunReport, RunStatus,
                               StageReport)
from ingest_common.season import Season
from ingest_common.settings import ConfigurationError, IngestSettings
from ingest_common.source import Source

EXIT_OK = 0
EXIT_ISSUES = 1
EXIT_USAGE = 2


class UsageError(Exception):
    pass


class ConsoleListener:
    """One progress line per stage on stderr."""

    def stage_started(self, stage: IngestStage) -> None:
        print(f"[{stage.value}] started", file=sys.stderr)

    def item_processed(self, stage: IngestStage, item: str) -> None:
        pass

    def stage_finished(self, report: StageReport) -> None:
        state = "FAILED" if report.failed else "finished"
        print(f"[{report.stage.value}] {state}", file=sys.stderr)


def _add_common(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--source", required=True, choices=[s.slug for s in Source], help="Federation source")
    parser.add_argument("--season", help="Season YYYY-YYYY (default: current season)")
    parser.add_argument("--data-dir", type=Path, help="Data directory (overrides TT_INGEST_DATA_DIR)")
    parser.add_argument("--json", action="store_true", dest="as_json", help="Print the run report as JSON")


def _add_filters(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--category", help="Category filter")
    parser.add_argument("--group", help="Group filter (BCNESA, FCTT)")
    parser.add_argument("--phase", help="Phase filter (BCNESA, FCTT)")
    parser.add_argument("--match-day", help="Match days: 3, 1,4 or 2-5")
    parser.add_argument("--gender", help="Gender filter (FCTT)")
    parser.add_argument("--territory", help="Download territory (BCNESA: Barcelona, Girona, Lleida, Tarragona; FCTT)")
    parser.add_argument("--force", action="store_true", help="Redo work even if outputs are up to date")


def _add_package(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--mode", choices=MODES, default="snapshot", help="Package mode (default: snapshot)")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be packaged without writing")
    parser.add_argument("--output", type=Path, help="ZIP file to write")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="tt-league-ingest", description="Table-tennis league data ingestion")
    commands = parser.add_subparsers(dest="command", required=True)

    for name, help_text in (("download", "Download federation content"), ("parse", "Parse content into actas-json")):
        sub = commands.add_parser(name, help=help_text)
        _add_common(sub)
        _add_filters(sub)
        sub.add_argument("--delay", type=float, help="Minimum seconds between requests")

    teams = commands.add_parser("teams", help="Download and parse teams (RFETM only)")
    _add_common(teams)
    teams.add_argument("--force", action="store_true")

    package = commands.add_parser("package", help="Build the upload ZIP")
    _add_common(package)
    package.add_argument("--match-day", help="Match days kept by a delta package")
    package.add_argument("--force", action="store_true", help="Overwrite an existing ZIP")
    _add_package(package)

    upload = commands.add_parser("upload", help="Upload a ZIP to the platform")
    _add_common(upload)
    upload.add_argument("--zip", required=True, type=Path, dest="zip_path")
    upload.add_argument("--allow-published-shrink", action="store_true")

    run = commands.add_parser("run", help="download + parse (+ package, + upload)")
    _add_common(run)
    _add_filters(run)
    run.add_argument("--delay", type=float, help="Minimum seconds between requests")
    run.add_argument("--package", action="store_true", dest="do_package", help="Also build the ZIP")
    run.add_argument("--upload", action="store_true", dest="do_upload", help="Also upload the ZIP (implies --package)")
    run.add_argument("--allow-published-shrink", action="store_true")
    _add_package(run)
    return parser


def build_request(args: argparse.Namespace) -> IngestRequest:
    try:
        season = Season.parse(args.season) if args.season else Season.current()
        match_days = parse_match_days(args.match_day) if getattr(args, "match_day", None) else None
    except ValueError as error:
        raise UsageError(str(error)) from error
    stages = {
        "download": (IngestStage.DOWNLOAD,),
        "parse": (IngestStage.PARSE,),
        "teams": (IngestStage.TEAMS,),
        "package": (IngestStage.PACKAGE,),
        "upload": (IngestStage.UPLOAD,),
        "run": (IngestStage.DOWNLOAD, IngestStage.PARSE)
               + ((IngestStage.PACKAGE,) if args.command == "run" and (args.do_package or args.do_upload) else ())
               + ((IngestStage.UPLOAD,) if args.command == "run" and args.do_upload else ()),
    }[args.command]
    mode = getattr(args, "mode", "snapshot")
    if mode == "delta" and not match_days:
        raise UsageError("--mode delta requires --match-day")
    zip_path = getattr(args, "zip_path", None) or getattr(args, "output", None)
    return IngestRequest(
        source=Source.parse(args.source), season=season, stages=stages,
        filters=IngestFilters(getattr(args, "category", None), getattr(args, "group", None),
                              getattr(args, "phase", None), match_days, getattr(args, "gender", None),
                              getattr(args, "territory", None)),
        force=getattr(args, "force", False), delay_seconds=getattr(args, "delay", None), mode=mode,
        allow_published_shrink=getattr(args, "allow_published_shrink", False), zip_path=zip_path,
        dry_run=getattr(args, "dry_run", False))


def exit_code(report: RunReport) -> int:
    return EXIT_OK if report.status is RunStatus.SUCCEEDED else EXIT_ISSUES


def format_summary(report: RunReport) -> str:
    lines = [f"{report.request.source.value} {report.request.season}: {report.status.value}"]
    for stage in report.stages:
        counters = ", ".join(f"{name}={value}" for name, value in stage.counters.items() if value)
        lines.append(f"  {stage.stage.value}: {counters or 'no counters'}")
        for where, message in stage.issues:
            lines.append(f"    ! {where}: {message}")
    for name, path in report.outputs.items():
        lines.append(f"  {name}: {path}")
    return "\n".join(lines)


def main(argv: Sequence[str] | None = None, *, ingestors: dict[Source, SourceIngestor] | None = None,
         listener: ProgressListener | None = None) -> int:
    parser = build_parser()
    try:
        args = parser.parse_args(argv)
    except SystemExit as exit_:
        return EXIT_USAGE if exit_.code else EXIT_OK
    try:
        request = build_request(args)
        settings = IngestSettings.build(args.data_dir)
        pipeline = IngestPipeline(settings, ingestors, listener or ConsoleListener())
    except (UsageError, ConfigurationError) as error:
        print(f"error: {error}", file=sys.stderr)
        return EXIT_USAGE
    report = pipeline.run(request)
    if args.as_json:
        print(json.dumps(report.to_dict(), indent=2, ensure_ascii=False))
    else:
        print(format_summary(report))
    return exit_code(report)


if __name__ == "__main__":
    sys.exit(main())
