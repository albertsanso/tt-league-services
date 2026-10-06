from __future__ import annotations

from ingest_common import progress
from ingest_common.run import IngestRequest, IngestStage, NoOpListener, StageReport
from ingest_common.pipeline import IngestPipeline
from ingest_common.season import Season
from ingest_common.settings import IngestSettings
from ingest_common.source import Source


class Recorder(NoOpListener):
    def __init__(self) -> None:
        self.events: list[tuple] = []

    def stage_started(self, stage):
        self.events.append(("started", stage))

    def stage_total(self, stage, total):
        self.events.append(("total", stage, total))

    def item_processed(self, stage, item):
        self.events.append(("item", stage, item))


def test_calls_without_a_bound_listener_do_nothing():
    progress.total(5)
    progress.item("page")


def test_bound_listener_receives_the_total_and_each_item_of_its_stage():
    recorder = Recorder()
    with progress.bind(recorder, IngestStage.PARSE):
        progress.total(2)
        progress.item("a.html")
        progress.item("b.html")
    assert recorder.events == [
        ("total", IngestStage.PARSE, 2), ("item", IngestStage.PARSE, "a.html"), ("item", IngestStage.PARSE, "b.html")]


def test_the_listener_is_unbound_after_the_block():
    recorder = Recorder()
    with progress.bind(recorder, IngestStage.DOWNLOAD):
        pass
    progress.item("late")
    assert recorder.events == []


def test_nested_binds_restore_the_outer_listener():
    outer, inner = Recorder(), Recorder()
    with progress.bind(outer, IngestStage.DOWNLOAD):
        with progress.bind(inner, IngestStage.PARSE):
            progress.item("inner")
        progress.item("outer")
    assert inner.events == [("item", IngestStage.PARSE, "inner")]
    assert outer.events == [("item", IngestStage.DOWNLOAD, "outer")]


class ScriptedIngestor:
    source = Source.FCTT
    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE})
    supported_filters = frozenset()

    def download(self, request, settings, listener):
        progress.total(2)
        progress.item("league one")
        progress.item("league two")
        return StageReport(IngestStage.DOWNLOAD)

    def parse(self, request, settings, listener):
        progress.total(1)
        progress.item("page.html")
        return StageReport(IngestStage.PARSE)

    def teams(self, request, settings, listener):  # pragma: no cover - not requested
        return StageReport(IngestStage.TEAMS)


def test_the_pipeline_routes_the_progress_of_each_stage_to_its_listener(tmp_path):
    recorder = Recorder()
    pipeline = IngestPipeline(IngestSettings(tmp_path), {Source.FCTT: ScriptedIngestor()}, recorder)

    pipeline.run(IngestRequest(Source.FCTT, Season.parse("2026-2027"), (IngestStage.DOWNLOAD, IngestStage.PARSE)))

    assert recorder.events == [
        ("started", IngestStage.DOWNLOAD),
        ("total", IngestStage.DOWNLOAD, 2),
        ("item", IngestStage.DOWNLOAD, "league one"),
        ("item", IngestStage.DOWNLOAD, "league two"),
        ("started", IngestStage.PARSE),
        ("total", IngestStage.PARSE, 1),
        ("item", IngestStage.PARSE, "page.html"),
    ]


def test_the_noop_listener_accepts_every_progress_call():
    listener = NoOpListener()
    listener.stage_started(IngestStage.DOWNLOAD)
    listener.stage_total(IngestStage.DOWNLOAD, 3)
    listener.item_processed(IngestStage.DOWNLOAD, "x")
    listener.stage_finished(StageReport(IngestStage.DOWNLOAD))
