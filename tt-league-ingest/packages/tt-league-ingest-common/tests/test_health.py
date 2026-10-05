from __future__ import annotations

from ingest_common import health
from ingest_common.run import COUNTERS, IngestFilters, IngestStage, StageReport
from ingest_common.scan import run_per_scope


def test_counters_declare_the_health_keys():
    assert {"http_errors", "timeouts", "parse_errors"} <= set(COUNTERS)
    assert StageReport(IngestStage.DOWNLOAD).counters["timeouts"] == 0


def test_collector_counts_each_kind():
    with health.collect() as counters:
        health.http_error()
        health.http_error()
        health.timeout()
        health.parse_error()
    assert (counters.http_errors, counters.timeouts, counters.parse_errors) == (2, 1, 1)


def test_calls_without_a_collector_do_nothing():
    health.http_error()
    health.timeout()
    health.parse_error()
    with health.collect() as counters:
        pass
    assert (counters.http_errors, counters.timeouts, counters.parse_errors) == (0, 0, 0)


def test_collector_is_removed_after_the_block():
    with health.collect() as outer:
        with health.collect() as inner:
            health.timeout()
        health.http_error()
    assert (inner.timeouts, inner.http_errors) == (1, 0)
    assert (outer.timeouts, outer.http_errors) == (0, 1)


def test_run_per_scope_sums_the_health_of_every_call_and_keeps_the_exit_code_rules():
    def main(argv: list[str]) -> int:
        health.http_error()
        if argv == ["--a"]:
            health.timeout()
            return 1
        health.parse_error()
        return 0

    report = StageReport(IngestStage.DOWNLOAD)
    calls = [(IngestFilters(category="A"), ["--a"]), (IngestFilters(category="B"), ["--b"])]
    run_per_scope(report, "script", calls, main, sleep=lambda _: None)
    assert (report.counters["http_errors"], report.counters["timeouts"], report.counters["parse_errors"]) == (2, 1, 1)
    assert not report.failed
    assert len(report.issues) == 1  # only exit code 1 is an issue: outcome rules are unchanged
