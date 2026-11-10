"""Golden tests: the unified fctt.cat implementation reproduces, byte for byte, the trees the original BCNESA and
FCTT ports produced for the saved pages under ``sites/`` (see ``golden_harness``)."""

from __future__ import annotations

import importlib.util

import pytest

from golden_adapters import FcttWebAdapter, LegacyAdapter
from golden_harness import differences, read_expected, run_scenario
from golden_scenarios import SCENARIOS

ADAPTERS = [FcttWebAdapter]
if importlib.util.find_spec("ingest_bcnesa") and importlib.util.find_spec("ingest_fctt"):
    ADAPTERS.append(LegacyAdapter)
if not importlib.util.find_spec("ingest_fcttweb"):
    ADAPTERS.remove(FcttWebAdapter)


@pytest.mark.parametrize("adapter_class", ADAPTERS, ids=lambda cls: cls.name)
@pytest.mark.parametrize("scenario", SCENARIOS, ids=lambda scenario: scenario.key)
def test_output_matches_golden(adapter_class, scenario):
    snapshots = run_scenario(adapter_class(), scenario)
    assert len(snapshots) == len(scenario.rounds)
    for number, snapshot in enumerate(snapshots, 1):
        problems = differences(read_expected(scenario, number), snapshot.files)
        assert not problems, f"{scenario.key} round {number}:\n" + "\n".join(problems)


def test_scenarios_cover_the_planned_cases():
    """The fixtures must keep exercising what the plan asks for (territory, comarcal, veterans, future, partial,
    walkover, women's league, duplicated pairing)."""
    bcnesa = {s.name for s in SCENARIOS if s.source == "bcnesa"}
    fctt = {s.name for s in SCENARIOS if s.source == "fctt"}
    assert bcnesa == {"territories", "faults", "published-guard"} and fctt == {"tdm", "femenina"}
