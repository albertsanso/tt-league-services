"""Incremental download of the current season's content from fctt.cat, for the profile's source.

Two download models exist because the original ports checked and stored pages differently (see ``SiteProfile``):
``validated-pages`` (``ingest_fcttweb.validated``) and ``calendar-checked`` (``ingest_fcttweb.calendar_checked``).
Both write ``<output_dir>/<season>/<category>/<group>/<phase>/<jornada file>`` through the profile's layout.
"""

from __future__ import annotations

from ingest_fcttweb import calendar_checked, validated
from ingest_fcttweb.profile import SiteProfile

MODELS = {"validated-pages": validated, "calendar-checked": calendar_checked}


def main(profile: SiteProfile, argv: list[str] | None = None) -> int:
    """Run the downloader of ``profile`` with script-style arguments; returns the script exit code."""
    return MODELS[profile.download_model].main(profile, argv)


def build_parser(profile: SiteProfile):
    return MODELS[profile.download_model].build_parser(profile)
