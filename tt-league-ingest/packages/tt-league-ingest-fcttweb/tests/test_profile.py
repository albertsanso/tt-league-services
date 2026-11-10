"""The profiles hold every legacy difference between the two ports; shared code never branches on the source."""

from __future__ import annotations

import dataclasses
import re
from pathlib import Path

import pytest

from ingest_common.source import Source
from ingest_fcttweb.profile import BCNESA_PROFILE, FCTT_PROFILE, PROFILES

SOURCE_DIR = Path(__file__).resolve().parents[1] / "src" / "ingest_fcttweb"


def test_profiles_are_frozen_and_keyed_by_the_unchanged_entry_point_names():
    assert PROFILES == {"bcnesa": BCNESA_PROFILE, "fctt": FCTT_PROFILE}
    assert (BCNESA_PROFILE.source, FCTT_PROFILE.source) == (Source.BCNESA, Source.FCTT)
    for profile in PROFILES.values():
        with pytest.raises(dataclasses.FrozenInstanceError):
            profile.emits_gender = not profile.emits_gender  # type: ignore[misc]


def test_http_etiquette_of_each_port_is_kept():
    bcnesa, fctt = BCNESA_PROFILE.http, FCTT_PROFILE.http
    assert (bcnesa.delay, fctt.delay) == (1.0, 3.0)
    assert (bcnesa.backoff, fctt.backoff, bcnesa.timeout, fctt.timeout) == (2.0, 5.0, 60.0, 30.0)
    assert bcnesa.retry_statuses == {403, 404, 408, 425, 429, 500, 502, 503, 504}
    assert fctt.retry_statuses == {404, 408, 425, 429, 500, 502, 503, 504, 520, 521, 522, 523, 524}
    assert 401 not in fctt.retry_statuses and 403 not in fctt.retry_statuses  # an explicit refusal is respected
    assert all(agent.startswith("Mozilla/5.0") for agent in bcnesa.user_agents)  # browser agents only
    assert fctt.user_agents[0].startswith("fctt-actas-downloader/") and fctt.user_agent_order == "by-attempt"


def test_layout_and_identity_formats_of_each_port_are_kept():
    bcnesa, fctt = BCNESA_PROFILE, FCTT_PROFILE
    assert bcnesa.layout.html_name.format(day=2) == "jornada_02.html"
    assert fctt.layout.html_name.format(day=2) == "jornada-2.html"
    assert bcnesa.layout.json_name.format(day=2, home=10, away=20) == "jornada_02_local_team_10_away_team_20.json"
    assert fctt.layout.json_name.format(day=2, home=10, away=20) == "jornada_2_local_team_10_away_team_20.json"
    assert (bcnesa.layout.default_phase, fctt.layout.default_phase) == ("1a Fase", "regular")
    assert (bcnesa.layout.meta.season, fctt.layout.meta.season) == ("season", "acta-season")
    assert (bcnesa.fixture_id, fctt.fixture_id) == ("compact", "slug")
    assert (bcnesa.emits_gender, fctt.emits_gender) == (False, True)
    assert (bcnesa.status_model, fctt.status_model) == ("table-present", "match-status")
    assert (bcnesa.parse_policy, fctt.parse_policy) == ("published-guard", "parse-state")
    assert (bcnesa.ingestor.delay_option, fctt.ingestor.delay_option) == ("--delay", "--request_delay")


def test_shared_code_never_branches_on_the_source():
    """Differences between BCNESA and FCTT are profile data; only the profiles and the entry-point classes name them."""
    allowed = {"profile.py", "ingestor.py"}
    pattern = re.compile(r"Source\.(BCNESA|FCTT)|== ?['\"](bcnesa|fctt)['\"]|\bBCNESA_PROFILE\b|\bFCTT_PROFILE\b")
    offenders = []
    for path in sorted(SOURCE_DIR.glob("*.py")):
        text = path.read_text(encoding="utf-8").split('if __name__ == "__main__":')[0]  # a script's default profile
        if path.name not in allowed and pattern.search(text):
            offenders.append(path.name)
    assert offenders == []
