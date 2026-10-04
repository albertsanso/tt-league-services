"""Incremental rule of the BCNESA downloader: ``complete`` jornadas are skipped, ``partial`` ones are refetched."""

from __future__ import annotations

import argparse
import logging
from types import SimpleNamespace

from bs4 import BeautifulSoup

from ingest_bcnesa import download as dl

LOGGER = logging.getLogger("test-bcnesa-incremental")
LOGGER.addHandler(logging.NullHandler())
LEAGUE = dl.League("Barcelona", "RTB PREFERENT G1", "rtb-preferent", "G1", "1a Fase",
                   "https://fctt.cat/lligues/rtb-preferent-g1/")
PAGE_URL = "https://fctt.cat/lligues/rtb-preferent-g1/?jornada=3"


def player(code, name):
    return f'<a class="br-apic-player-link" href="?codi_jugador={code}&amp;jornada=3">{name}</a>'


def played_match(home="10", away="20", acta="https://fctt.cat/ligas/partido/555/imprimir/acta"):
    row = ('<tr><td class="position">A</td><td class="player-info">' + player(1, "ANNA") + '</td>'
           '<td class="position">X</td><td class="player-info">' + player(2, "BERTA") + '</td>'
           '<td class="game">11-5</td><td class="result">1-0</td><td class="global">1-0</td></tr>')
    return (f'<div class="match-container"><div class="match-datetime">04/10/2026 · 11:00</div>'
            f'<div class="match-teams"><div class="team-home"><a href="?team_name_id={home}">HOME</a></div>'
            f'<div class="match-score">1-0</div>'
            f'<div class="team-away"><a href="?team_name_id={away}">AWAY</a></div></div>'
            f'<table class="match-results-table"><tbody>{row}</tbody></table>'
            f'<div class="match-info"><div class="field-info"><strong>Terreny de joc:</strong> C/ MAJOR 1</div>'
            f'<a class="acta-link" href="{acta}">Descarregar acta</a></div></div>')


def pending_match():
    return ('<div class="match-container"><div class="match-datetime">11/10/2026 · 11:00</div>'
            '<div class="match-teams"><div class="team-home"><a href="?team_name_id=30">HOME2</a></div>'
            '<div class="match-score"></div>'
            '<div class="team-away"><a href="?team_name_id=40">AWAY2</a></div></div>'
            '<div class="match-info"><div class="field-info"><strong>Terreny de joc:</strong> C/ MENOR 2</div></div></div>')


class FakeFetcher:
    """Serves a fixed jornada page through the real validation check, like the HTTP fetcher does."""

    def __init__(self, html: str):
        self.html = html
        self.requests: list[str] = []
        self.last = SimpleNamespace(retries=0, errors=[], outcome="ok")

    def get(self, url, check, kind, key=None):
        self.requests.append(url)
        if kind == "pdf":
            return b"%PDF-1.4 fake"
        response = SimpleNamespace(content=self.html.encode("utf-8"))
        try:
            return check(response)
        except dl.IncompleteContent as error:
            self.last = SimpleNamespace(retries=0, errors=[str(error)], outcome="incomplete")
            return error.result


class RecordingMetrics:
    def __init__(self):
        self.outcomes: list[str] = []

    def record_match_day(self, key, outcome, *args, **kwargs):
        self.outcomes.append(outcome)

    def persistently_incomplete(self, key):
        return False

    def count(self, name, amount=1):
        pass


def run(tmp_path, fetcher, expected_matches=None, scheduled=None, **overrides):
    args = argparse.Namespace(**{"season": "2026-2027", "format": "html", "skip_pdf": True, "force": False,
                                 **overrides})
    metrics = RecordingMetrics()
    ctx = dl.Context(fetcher, metrics, args, LOGGER, adaptive=True)
    if expected_matches:
        scheduled = SimpleNamespace(containers=[object()] * expected_matches, heading=None, future=False)
    dl.process_match_day(ctx, LEAGUE, "1a Fase", 3, tmp_path, scheduled, LEAGUE.title)
    return metrics.outcomes, tmp_path / "jornada_03.html"


def meta(path):
    return dl.read_saved(path)[1]


def test_complete_jornada_is_not_requested_again(tmp_path):
    first = FakeFetcher(played_match())
    outcomes, saved = run(tmp_path, first)
    assert meta(saved)[dl.STATUS_META] == "complete" and outcomes == ["new"]

    again = FakeFetcher(played_match())
    outcomes, _ = run(tmp_path, again)
    assert again.requests == [] and outcomes == ["skipped"]


def test_partial_jornada_is_requested_again_and_upgraded_when_it_completes(tmp_path):
    pending = FakeFetcher(played_match() + pending_match())
    outcomes, saved = run(tmp_path, pending)
    assert meta(saved)[dl.STATUS_META] == "partial" and pending.requests == [PAGE_URL]
    assert outcomes == ["new"]  # a pending match is valid minimal content, saved as partial

    still_pending = FakeFetcher(played_match() + pending_match())
    run(tmp_path, still_pending)
    assert still_pending.requests == [PAGE_URL]  # partial content is retried on every run

    finished = FakeFetcher(played_match() + played_match("30", "40", "https://fctt.cat/ligas/partido/556/imprimir/acta"))
    outcomes, saved = run(tmp_path, finished)
    assert finished.requests == [PAGE_URL] and outcomes == ["updated"]
    assert meta(saved)[dl.STATUS_META] == "complete"

    after = FakeFetcher("")
    outcomes, _ = run(tmp_path, after)
    assert after.requests == [] and outcomes == ["skipped"]


def test_force_refetches_a_complete_jornada(tmp_path):
    run(tmp_path, FakeFetcher(played_match()))
    forced = FakeFetcher(played_match())
    run(tmp_path, forced, force=True)
    assert forced.requests == [PAGE_URL]


def test_complete_jornada_missing_pdfs_is_requested_again_when_pdfs_are_wanted(tmp_path):
    run(tmp_path, FakeFetcher(played_match()))
    wants_pdf = FakeFetcher(played_match())
    args_outcomes, _ = run(tmp_path, wants_pdf, format="both", skip_pdf=False)
    assert wants_pdf.requests[0] == PAGE_URL  # complete HTML but its PDF acta is missing: not skipped
    assert args_outcomes[-1] != "skipped"
    assert wants_pdf.requests[1:] == ["https://fctt.cat/ligas/partido/555/imprimir/acta"]

    complete_with_pdf = FakeFetcher(played_match())
    outcomes, _ = run(tmp_path, complete_with_pdf, format="both", skip_pdf=False)
    assert complete_with_pdf.requests == [] and outcomes == ["skipped"]


def future_schedule(date="11/10/2099"):
    html = pending_match().replace("11/10/2026", date)
    return SimpleNamespace(containers=BeautifulSoup(html, "html.parser").select(".match-container"),
                           heading=None, future=True)


def test_downloaded_future_jornada_is_not_refreshed_without_force(tmp_path):
    fetcher = FakeFetcher("")
    outcomes, saved = run(tmp_path, fetcher, scheduled=future_schedule())
    assert fetcher.requests == [] and outcomes == ["future"]  # minimal info taken from the league page
    first = saved.read_bytes()

    moved = FakeFetcher("")
    outcomes, saved = run(tmp_path, moved, scheduled=future_schedule("18/10/2099"))
    assert moved.requests == [] and outcomes == ["skipped"]
    assert saved.read_bytes() == first  # the calendar change is ignored until --force


def test_force_refreshes_a_downloaded_future_jornada(tmp_path):
    run(tmp_path, FakeFetcher(""), scheduled=future_schedule())
    outcomes, saved = run(tmp_path, FakeFetcher(""), scheduled=future_schedule("18/10/2099"), force=True)
    assert outcomes == ["future"] and "18/10/2099" in saved.read_text(encoding="utf-8")


def test_status_scan_classifies_saved_jornadas(tmp_path):
    from datetime import datetime

    from ingest_bcnesa import status
    from ingest_common.match_day_status import build_report
    from ingest_common.source import Source

    content = tmp_path / "content"
    groups = {"G1": played_match(), "G2": played_match() + pending_match()}
    for group, html in groups.items():
        league_dir = content / "2026-2027" / "rtb-preferent" / group / "1a Fase"
        league_dir.mkdir(parents=True)
        run(league_dir, FakeFetcher(html))  # saves jornada_03.html
    report = build_report(Source.BCNESA, status.scan(content), datetime(2026, 10, 4, 12, 0))
    by_file = {day["file"]: day["status"] for day in report["matchDays"]}
    assert by_file == {"2026-2027/rtb-preferent/G1/1a Fase/jornada_03.html": "complete",
                       "2026-2027/rtb-preferent/G2/1a Fase/jornada_03.html": "partial"}
    first = report["matchDays"][0]
    assert (first["matchDay"], first["category"], first["territory"]) == (3, "rtb-preferent", "Barcelona")


def test_status_row_is_a_valid_scope_for_download_parse_and_package(tmp_path):
    """The orchestrator builds scopes from status rows: each row value must select that same league again."""
    from datetime import datetime
    from pathlib import PurePosixPath

    from ingest_bcnesa import parse, status
    from ingest_bcnesa.ingestor import BcnesaIngestor
    from ingest_common.match_day_status import build_report
    from ingest_common.scopes import scope_from_values
    from ingest_common.source import Source

    league = dl.League("Barcelona", "RTB PREFERENT G1", "RTB PREFERENT", "G1", "1a Fase", LEAGUE.url)
    content = tmp_path / "content"
    league_dir = content / "2026-2027" / dl.clean_name(league.category) / league.group / "1a Fase"
    league_dir.mkdir(parents=True)
    ctx = dl.Context(FakeFetcher(played_match()), RecordingMetrics(),
                     argparse.Namespace(season="2026-2027", format="html", skip_pdf=True, force=False), LOGGER,
                     adaptive=True)
    dl.process_match_day(ctx, league, "1a Fase", 3, league_dir, None, league.title)

    row = build_report(Source.BCNESA, status.scan(content), datetime(2026, 10, 4, 12, 0))["matchDays"][0]
    scope = scope_from_values(category=row["category"], group=row["group"], phase=row["phase"],
                              territory=row["territory"], match_days=[row["matchDay"]])

    assert dl.league_matches_filters(league, argparse.Namespace(territory=scope.territory, category=scope.category,
                                                                group=scope.group))
    selected = parse.find_jornada_files(content, argparse.Namespace(
        season="2026-2027", category=scope.category, group=scope.group, phase=scope.phase,
        match_day=set(scope.match_days)))
    assert [path.relative_to(content).as_posix() for path in selected] == [row["file"]]
    acta = PurePosixPath("rtb-preferent/G1/1a Fase/jornada_03_local_team_10_away_team_20.json")
    assert BcnesaIngestor().scope_matches(scope, acta, row["matchDay"])
