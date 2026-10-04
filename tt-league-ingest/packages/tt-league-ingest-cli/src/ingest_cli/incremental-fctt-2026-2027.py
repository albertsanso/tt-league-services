"""Incremental FCTT ingestion for season 2026-2027: download, parse and package.

Usage (from ``tt-league-ingest/``, with ``TT_INGEST_DATA_DIR`` set or ``--data-dir`` given)::

    uv run python packages/tt-league-ingest-cli/src/ingest_cli/incremental-fctt-2026-2027.py --match-day 5
    uv run python packages/tt-league-ingest-cli/src/ingest_cli/incremental-fctt-2026-2027.py            # snapshot

With ``--match-day`` the ZIP is a delta holding only the actas of those match days; without it the
ZIP is a snapshot of the whole season. Any other option is passed through to
``tt-league-ingest run`` (for example ``--category``, ``--group``, ``--phase``, ``--force``,
``--output``, ``--upload``). The exit code is the one of ``tt-league-ingest`` (0 ok, 1 issues, 2 usage).
"""

from __future__ import annotations

import sys

from ingest_cli.main import main

SOURCE = "fctt"
SEASON = "2026-2027"
DATA_DIR = "D:\\tt-league-data"


def build_arguments(extra: list[str]) -> list[str]:
    arguments = ["run", "--source", SOURCE, "--season", SEASON, "--data-dir", DATA_DIR, "--package"]
    has_match_day = any(argument == "--match-day" or argument.startswith("--match-day=") for argument in extra)
    if has_match_day and "--mode" not in extra:
        arguments += ["--mode", "delta"]
    return arguments + extra


if __name__ == "__main__":
    sys.exit(main(build_arguments(sys.argv[1:])))
