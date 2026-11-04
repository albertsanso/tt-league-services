from __future__ import annotations

import json
import shutil
from pathlib import Path

import pytest

from ingest_common.validation import ActaValidator, TeamsValidator
from ingest_rfetm import download, parse, teams_parse
from ingest_rfetm.ingestor import RfetmIngestor

FIXTURES = Path(__file__).parent / "fixtures"
GROUP = Path("2026-2027/divisio-honor/1/femenino")


@pytest.fixture
def content(tmp_path):
    shutil.copytree(FIXTURES / "content", tmp_path / "content")
    return tmp_path


def convert(root: Path, *extra: str) -> int:
    return parse.main(["--content-dir", str(root / "content"), "--json-dir", str(root / "json"),
                       "--season", "2026-2027", "--validate", *extra])


def test_the_parse_reports_the_page_count_and_each_finished_page(content):
    from ingest_common import progress
    from ingest_common.run import IngestStage, NoOpListener

    class Recorder(NoOpListener):
        def __init__(self):
            self.events = []

        def stage_total(self, stage, total):
            self.events.append(("total", total))

        def item_processed(self, stage, item):
            self.events.append(("item", item))

    recorder = Recorder()

    with progress.bind(recorder, IngestStage.PARSE):
        assert convert(content) == 0

    pages = len(list((content / "content" / "2026-2027").glob("*/*/*/grupo_*.html")))
    assert pages > 0
    assert recorder.events[0] == ("total", pages)
    items = [item for kind, item in recorder.events if kind == "item"]
    assert len(items) == pages
    assert recorder.events.count(("total", pages)) == 1


def test_category_mapping_and_unknown_code_behaviour():
    assert download.map_liga_to_category("MQ==") == "super-divisio"
    assert download.map_liga_to_category("Mg==") == "divisio-honor"
    # legacy behaviour kept by the port: unknown codes get a liga-<code> folder instead of failing
    assert download.map_liga_to_category("ZZ==") == "liga-zz"


def test_acta_links_are_extracted_from_saved_jornada_html():
    html = (FIXTURES / "content" / GROUP / "grupo_1.html").read_text(encoding="utf-8", errors="replace")
    ids = [partido_id for _, partido_id in download.extract_acta_links(html)]
    assert ids == ["32467", "32468", "32469"]


def test_pdf_enriched_actas_match_the_legacy_output_byte_for_byte(content):
    assert convert(content) == 0
    produced = sorted((content / "json" / GROUP).glob("acta_*.json"))
    expected = sorted((FIXTURES / "expected" / GROUP).glob("acta_*.json"))
    assert [p.name for p in produced] == [p.name for p in expected] and produced
    validator = ActaValidator()
    for actual, reference in zip(produced, expected):
        assert actual.read_bytes() == reference.read_bytes()
        payload = json.loads(actual.read_text(encoding="utf-8"))
        assert payload["acta_publicada"] is True and validator.errors(payload) == []


def test_html_only_actas_are_unpublished(content):
    for pdf in (content / "content" / GROUP).glob("*.pdf"):
        pdf.unlink()
    assert convert(content) == 0
    payloads = [json.loads(p.read_text(encoding="utf-8")) for p in (content / "json" / GROUP).glob("acta_*.json")]
    assert payloads and all(payload["acta_publicada"] is False for payload in payloads)
    assert all(ActaValidator().errors(payload) == [] for payload in payloads)


def test_existing_json_is_kept_unless_forced(content):
    assert convert(content) == 0
    target = next((content / "json" / GROUP).glob("acta_*.json"))
    target.write_text("{}", encoding="utf-8")
    assert convert(content) == 0 and target.read_text(encoding="utf-8") == "{}"
    assert convert(content, "--force") == 0 and target.read_text(encoding="utf-8") != "{}"


def test_json_is_regenerated_when_its_html_or_pdf_is_newer(content):
    import os
    assert convert(content) == 0
    targets = sorted((content / "json" / GROUP).glob("acta_*.json"))
    for target in targets:
        target.write_text("{}", encoding="utf-8")
        os.utime(target, (1_000_000, 1_000_000))  # older than the refreshed HTML/PDF
    assert convert(content) == 0
    assert all(target.read_text(encoding="utf-8") != "{}" for target in targets)


def test_missing_content_season_is_reported(tmp_path):
    (tmp_path / "content").mkdir()
    assert convert(tmp_path) == 1


def test_teams_parse_matches_legacy_json_and_schema(tmp_path):
    html_dir = tmp_path / "equipos"
    html_dir.mkdir()
    shutil.copy(FIXTURES / "content" / "equipos-2026-2027.html", html_dir / "2026-2027.html")
    output = tmp_path / "json" / "{season}.json"
    assert teams_parse.main(["--input-dir", str(html_dir), "--output-file", str(output), "--season", "2026-2027"]) == 0
    produced = tmp_path / "json" / "2026-2027.json"
    assert produced.read_bytes() == (FIXTURES / "expected" / "equipos-2026-2027.json").read_bytes()
    assert TeamsValidator().errors(json.loads(produced.read_text(encoding="utf-8"))) == []


def test_ingestor_declares_all_stages_and_filters():
    ingestor = RfetmIngestor()
    assert {stage.value for stage in ingestor.supported_stages} == {"DOWNLOAD", "PARSE", "TEAMS", "PACKAGE", "UPLOAD"}
    assert ingestor.supported_filters == {"category", "match_days"}


# --------------------------------------------------------------------------- incremental download

import re  # noqa: E402
from datetime import datetime  # noqa: E402
from types import SimpleNamespace  # noqa: E402

COMPLETE_HTML = (FIXTURES / "content" / GROUP / "grupo_1.html").read_text(encoding="utf-8", errors="replace")
NOT_PLAYED_HTML = re.sub(r"<a[^>]*imprimir/acta[^>]*>.*?</a>", "", COMPLETE_HTML, flags=re.S)
BEFORE, DURING = datetime(2026, 9, 1), datetime(2026, 10, 1)


class FakeDownloader:
    def __init__(self, body: str | None):
        self.body, self.urls = body, []

    def get(self, url):
        self.urls.append(url)
        if self.body is None:
            return None
        return SimpleNamespace(status_code=200, content=self.body.encode("utf-8"))


def fetch(tmp_path, saved: str | None, served: str | None, now=DURING, force=False):
    download.configure(tmp_path / "content")
    path = tmp_path / "content" / "grupo_1.html"
    path.parent.mkdir(exist_ok=True)
    if saved is not None:
        path.write_bytes(saved.encode("utf-8"))
    downloader, stats = FakeDownloader(served), download.Stats()
    html = download.download_jornada_html(downloader, {"url": "https://rfetm/j1"}, path, force, stats, now=now)
    return html, downloader.urls, stats, path


def test_jornada_status():
    assert download.jornada_status(COMPLETE_HTML, DURING) == "complete"
    assert download.jornada_status(NOT_PLAYED_HTML, BEFORE) == "future"
    assert download.jornada_status(NOT_PLAYED_HTML, DURING) == "partial"
    assert download.jornada_status("<html><body><table></table></body></html>", DURING) == "empty"


@pytest.mark.parametrize("text,expected", [
    ("26/09/2026 16:00", ("2026-09-26", "16:00")),
    ("00/00/0000 16:00", (None, "16:00")),
    ("31/02/2026 25:99", (None, None)),
])
def test_invalid_match_dates_and_times_are_dropped(text, expected):
    assert parse.parse_fecha_hora(text) == expected


def test_unparseable_stored_date_does_not_break_jornada_status():
    placeholder = {"fecha": "0000-00-00", "hora": None}
    assert download._match_start(placeholder) is None
    assert download._match_start({"fecha": "2026-09-26", "hora": "16:00"}) == datetime(2026, 9, 26, 16, 0)


@pytest.mark.parametrize("saved,now", [(COMPLETE_HTML, DURING), (NOT_PLAYED_HTML, BEFORE),
                                       ("<html><body>sin partidos</body></html>", DURING)],
                         ids=["complete", "future", "empty"])
def test_complete_future_and_empty_jornadas_are_not_requested_again(tmp_path, saved, now):
    html, urls, stats, _ = fetch(tmp_path, saved, "<html>new</html>", now=now)
    assert urls == [] and html == saved and stats.html_saltados == 1


def test_partial_jornada_is_requested_again_and_saved(tmp_path):
    html, urls, stats, path = fetch(tmp_path, NOT_PLAYED_HTML, COMPLETE_HTML)
    assert urls == ["https://rfetm/j1"] and html == COMPLETE_HTML
    assert stats.html_actualizados == 1 and path.read_bytes() == COMPLETE_HTML.encode("utf-8")
    # once complete, the next run does not ask again
    _, urls, _, _ = fetch(tmp_path, None, "<html>new</html>")
    assert urls == []


def test_failed_refresh_keeps_the_saved_page(tmp_path):
    html, urls, stats, path = fetch(tmp_path, NOT_PLAYED_HTML, None)
    assert urls == ["https://rfetm/j1"] and html == NOT_PLAYED_HTML and stats.errores
    assert path.read_bytes() == NOT_PLAYED_HTML.encode("utf-8")


def test_force_requests_complete_and_future_jornadas(tmp_path):
    _, urls, _, _ = fetch(tmp_path, COMPLETE_HTML, COMPLETE_HTML, force=True)
    assert urls == ["https://rfetm/j1"]
    _, urls, _, _ = fetch(tmp_path, NOT_PLAYED_HTML, NOT_PLAYED_HTML, now=BEFORE, force=True)
    assert urls == ["https://rfetm/j1"]


def test_new_jornada_is_downloaded(tmp_path):
    html, urls, stats, path = fetch(tmp_path, None, NOT_PLAYED_HTML, now=BEFORE)
    assert urls == ["https://rfetm/j1"] and stats.html_descargados == 1 and path.is_file()


def test_status_scan_of_saved_jornadas(tmp_path):
    from ingest_common.match_day_status import build_report
    from ingest_common.source import Source
    from ingest_rfetm import status

    content = tmp_path / "content"
    for day, html in ((1, COMPLETE_HTML), (2, NOT_PLAYED_HTML), (3, "<html><body>sin partidos</body></html>")):
        folder = content / "2026-2027" / "divisio-honor" / str(day) / "femenino"
        folder.mkdir(parents=True)
        (folder / "grupo_1.html").write_bytes(html.encode("utf-8"))
    (content / "equipos").mkdir()
    (content / "equipos" / "2026-2027.html").write_text("<html></html>", encoding="utf-8")  # not a jornada page

    # day 2 keeps its scores but has no acta links: played and not reported -> partial
    before = build_report(Source.RFETM, status.scan(content), BEFORE)
    assert [(d["matchDay"], d["status"]) for d in before["matchDays"]] == [(1, "complete"), (2, "partial")]
    assert before["summary"]["emptyPages"] == 1
    day = before["matchDays"][0]
    assert (day["category"], day["group"], day["gender"], day["file"]) == (
        "divisio-honor", "1", "femenino", "2026-2027/divisio-honor/1/femenino/grupo_1.html")

    unplayed = re.sub(r"(<div style=''>)\s*\d+\s*(</b>)", r"\1 \2", NOT_PLAYED_HTML)  # drop the scores too
    (content / "2026-2027" / "divisio-honor" / "2" / "femenino" / "grupo_1.html").write_bytes(unplayed.encode("utf-8"))
    assert [d["status"] for d in build_report(Source.RFETM, status.scan(content), BEFORE)["matchDays"]] == [
        "complete", "future"]
    assert [d["status"] for d in build_report(Source.RFETM, status.scan(content), DURING)["matchDays"]] == [
        "complete", "scheduled"]


# --------------------------------------------------------------------------- scoped runs

def test_each_scope_runs_download_and_parse_once(tmp_path, monkeypatch):
    from ingest_common.run import IngestFilters, IngestRequest, IngestStage, NoOpListener
    from ingest_common.season import Season
    from ingest_common.settings import IngestSettings
    from ingest_common.source import Source

    calls = {"download": [], "parse": []}
    monkeypatch.setattr(download, "main", lambda argv: calls["download"].append(argv) or 0)
    monkeypatch.setattr(parse, "main", lambda argv: calls["parse"].append(argv) or 0)
    scopes = (IngestFilters(category="divisio-honor", match_days=frozenset({3, 4})),
              IngestFilters(category="primera-divisio"))
    request = IngestRequest(Source.RFETM, Season.parse("2026-2027"), (IngestStage.DOWNLOAD, IngestStage.PARSE),
                            delay_seconds=0, scopes=scopes)
    ingestor, settings = RfetmIngestor(), IngestSettings(tmp_path)
    assert not ingestor.download(request, settings, NoOpListener()).failed
    assert not ingestor.parse(request, settings, NoOpListener()).failed
    assert [argv[argv.index("--category") + 1:] for argv in calls["download"]] == [
        ["divisio-honor", "--delay", "0"], ["primera-divisio", "--delay", "0"]]
    assert calls["download"][0].count("--jornada") == 2 and "--jornada" not in calls["download"][1]
    assert [argv[argv.index("--validate") + 1:] for argv in calls["parse"]] == [
        ["--jornada", "3", "--jornada", "4", "--category", "divisio-honor"], ["--category", "primera-divisio"]]


@pytest.mark.parametrize("scope, day, expected", [
    ({"category": "divisio-honor"}, 1, True),
    ({"category": "primera-divisio"}, 1, False),
    ({"match_days": frozenset({1, 2})}, 1, True),
    ({"category": "divisio-honor", "match_days": frozenset({2})}, 1, False),
])
def test_scope_matches_follows_the_actas_layout(scope, day, expected):
    from pathlib import PurePosixPath

    from ingest_common.run import IngestFilters

    acta = PurePosixPath("divisio-honor/1/femenino/acta_866_1113.json")
    assert RfetmIngestor().scope_matches(IngestFilters(**scope), acta, day) is expected
    assert not RfetmIngestor().scope_matches(IngestFilters(**scope), PurePosixPath("divisio-honor/acta.json"), day)


def test_status_row_category_and_match_day_are_a_valid_scope():
    """RFETM rows also carry group and gender, which RFETM scopes do not support: scopes use category and day."""
    from pathlib import PurePosixPath

    from ingest_common.match_day_status import build_report
    from ingest_common.scopes import scope_from_values
    from ingest_common.source import Source
    from ingest_rfetm import status

    row = build_report(Source.RFETM, status.scan(FIXTURES / "content"), DURING)["matchDays"][0]
    scope = scope_from_values(category=row["category"], match_days=[row["matchDay"]])
    assert scope.category in download.LIGA_MAPPING.values()  # accepted by the downloader's --category choices
    parse.configure(FIXTURES / "content", Path("unused"))
    found = list(parse.iter_html_files(row["season"], scope.category, sorted(scope.match_days)))
    assert [path.relative_to(FIXTURES / "content").as_posix() for *_, path in found] == [row["file"]]
    acta = PurePosixPath(row["category"], str(row["matchDay"]), row["gender"], "acta_866_1113.json")
    assert RfetmIngestor().scope_matches(scope, acta, row["matchDay"])
