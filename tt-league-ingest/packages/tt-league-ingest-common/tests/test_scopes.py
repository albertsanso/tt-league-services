from __future__ import annotations

import pytest

from ingest_common.run import IngestFilters, IngestRequest, IngestStage, StageReport
from ingest_common.scan import run_per_scope
from ingest_common.scopes import parse_scopes, scope_from_values, scopes_from_values
from ingest_common.season import Season
from ingest_common.source import Source

SEASON = Season.parse("2026-2027")
DOWNLOAD = (IngestStage.DOWNLOAD,)


def test_scope_values_are_trimmed_and_match_days_accept_a_list_or_a_selector():
    assert scope_from_values(category=" tdm ", group="", match_days=[4, 1]) == IngestFilters(
        category="tdm", match_days=frozenset({1, 4}))
    assert scope_from_values(territory="Girona", match_days="2-4").match_days == frozenset({2, 3, 4})


@pytest.mark.parametrize("values, message", [
    ({}, "at least one"),
    ({"category": "  "}, "at least one"),
    ({"category": 3}, "category must be text"),
    ({"match_days": []}, "must not be empty"),
    ({"match_days": [0]}, "positive integers"),
    ({"match_days": [True]}, "positive integers"),
    ({"match_days": "x"}, "invalid match day"),
    ({"match_days": {"a": 1}}, "array of integers"),
])
def test_invalid_scope_values_are_rejected(values, message):
    with pytest.raises(ValueError, match=message):
        scope_from_values(**values)


def test_parse_scopes_document_deduplicates_and_names_the_bad_scope():
    scopes = parse_scopes({"scopes": [{"category": "tdm", "matchDays": [3]}, {"group": "g2"},
                                      {"category": "tdm", "matchDays": "3"}]})
    assert scopes == (IngestFilters(category="tdm", match_days=frozenset({3})), IngestFilters(group="g2"))
    with pytest.raises(ValueError, match=r"scope 2: unknown key\(s\) grup"):
        parse_scopes({"scopes": [{"group": "g1"}, {"grup": "g1"}]})
    with pytest.raises(ValueError, match="scope 1: must be an object"):
        parse_scopes({"scopes": ["tdm"]})
    with pytest.raises(ValueError, match="must not be empty"):
        parse_scopes({"scopes": []})
    with pytest.raises(ValueError, match="single key"):
        parse_scopes([{"group": "g1"}])
    with pytest.raises(ValueError, match="single key"):
        parse_scopes({"scopes": [], "filters": {}})
    with pytest.raises(ValueError, match="scope 1: a scope must set"):
        scopes_from_values([{"matchDays": None}])


def test_request_keeps_first_seen_scopes_and_exposes_effective_scopes():
    a, b = IngestFilters(group="g1"), IngestFilters(group="g2")
    scoped = IngestRequest(Source.FCTT, SEASON, DOWNLOAD, scopes=(a, b, a))
    assert scoped.scopes == (a, b) and scoped.effective_scopes() == (a, b)
    plain = IngestRequest(Source.FCTT, SEASON, DOWNLOAD, IngestFilters(category="tdm"))
    assert plain.effective_scopes() == (IngestFilters(category="tdm"),)


@pytest.mark.parametrize("kwargs, message", [
    ({"filters": IngestFilters(category="tdm"), "scopes": (IngestFilters(group="g1"),)}, "not both"),
    ({"scopes": (IngestFilters(group="g1"), IngestFilters())}, "scope 2 sets no field"),
    ({"stages": (IngestStage.DOWNLOAD, IngestStage.PACKAGE), "scopes": (IngestFilters(group="g1"),)}, "delta"),
])
def test_request_rejects_invalid_scope_combinations(kwargs, message):
    arguments = {"stages": DOWNLOAD, **kwargs}
    with pytest.raises(ValueError, match=message):
        IngestRequest(Source.FCTT, SEASON, **arguments)


def test_scoped_delta_package_is_allowed():
    request = IngestRequest(Source.FCTT, SEASON, (IngestStage.PARSE, IngestStage.PACKAGE), mode="delta",
                            scopes=(IngestFilters(group="g1"),))
    assert request.scopes


def test_scope_description_and_dict():
    scope = IngestFilters(category="PREFERENT", group="G2", match_days=frozenset({4, 3}), territory="Girona")
    assert scope.describe() == "territory=Girona category=PREFERENT group=G2 days=3,4"
    assert IngestFilters().describe() == "all"
    assert scope.to_dict() == {"category": "PREFERENT", "group": "G2", "phase": None, "territory": "Girona",
                               "gender": None, "matchDays": [3, 4]}


class Script:
    def __init__(self, *codes):
        self.codes, self.calls = list(codes), []

    def __call__(self, argv):
        self.calls.append(argv)
        return self.codes.pop(0) if self.codes else 0


def test_run_per_scope_runs_distinct_argument_lists_once_with_a_pause_between():
    script, pauses = Script(), []
    a, b, c = IngestFilters(group="g1"), IngestFilters(group="g2"), IngestFilters(group="g1", territory="x")
    report = StageReport(IngestStage.DOWNLOAD)
    run_per_scope(report, "fake", [(a, ["--group", "g1"]), (b, ["--group", "g2"]), (c, ["--group", "g1"])],
                  script, 2.5, pauses.append)
    assert script.calls == [["--group", "g1"], ["--group", "g2"]]
    assert pauses == [2.5] and not report.issues


def test_run_per_scope_labels_issues_per_scope_and_stops_at_a_failing_call():
    a, b, c = IngestFilters(group="g1"), IngestFilters(group="g2"), IngestFilters(group="g3")
    report = StageReport(IngestStage.DOWNLOAD)
    script = Script(1, 2, 0)
    run_per_scope(report, "fake", [(a, ["1"]), (b, ["2"]), (c, ["3"])], script, 0, lambda _: None)
    assert len(script.calls) == 2 and report.failed
    assert [where for where, _ in report.issues] == ["fake [group=g1]", "fake [group=g2]"]
    assert report.legacy_failure


def test_run_per_scope_with_one_call_keeps_the_plain_label():
    report = StageReport(IngestStage.PARSE)
    run_per_scope(report, "fake parse", [(IngestFilters(), ["x"])], Script(1), 0, lambda _: None)
    assert report.issues[0][0] == "fake parse"
