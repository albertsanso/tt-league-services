"""Side channel reporting how far a legacy download or parse script is through its work.

The legacy scripts only return an exit code, so they report the size of their work list through ``total()`` and each
finished item through ``item()``. Like ``ingest_common.health``, the calls do nothing unless a sink installed by
``bind()`` is active, which is the case when the pipeline runs a stage and when a script runs standalone.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass

from ingest_common.run import IngestStage, ProgressListener


@dataclass(frozen=True)
class _Sink:
    listener: ProgressListener
    stage: IngestStage


_sink: ContextVar[_Sink | None] = ContextVar("ingest_progress_sink", default=None)


@contextmanager
def bind(listener: ProgressListener, stage: IngestStage) -> Iterator[None]:
    """Route the progress calls of the block to ``listener`` as items of ``stage``."""
    token = _sink.set(_Sink(listener, stage))
    try:
        yield
    finally:
        _sink.reset(token)


def total(count: int) -> None:
    """The number of items the stage is going to process, once the work list is known."""
    active = _sink.get()
    if active is not None:
        active.listener.stage_total(active.stage, count)


def item(label: str) -> None:
    """One item (a page, a league, a document) is done; ``label`` names it."""
    active = _sink.get()
    if active is not None:
        active.listener.item_processed(active.stage, label)
