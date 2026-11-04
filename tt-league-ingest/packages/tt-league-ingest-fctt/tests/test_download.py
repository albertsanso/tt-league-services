import importlib.util
import io
import json
import logging
import sys
import tempfile
import unittest
from datetime import datetime
from email.message import Message
from pathlib import Path
from urllib.error import HTTPError


from ingest_fctt import download as dl
dl.LOGGER.addHandler(logging.NullHandler())
dl.LOGGER.propagate = False

SAVED_PAGES = Path(__file__).resolve().parent / "fixtures" / "saved-pages"
NOW = datetime(2026, 10, 2, 12, 0, tzinfo=dl.LOCAL_TZ)

INDEX_HTML = """<html><head><title>Competicions</title></head><body>
<nav><a href="https://fctt.cat/lligues/altres/">Menú</a></nav>
<h2>TDM</h2>
<h3>TDM G1</h3><a class="btn" href="https://fctt.cat/lligues/grup-1/"><span>Veure</span></a>
<h3>TDM G2</h3><a class="btn" href="https://fctt.cat/lligues/grup-2-2/"></a>
<h2>Altres temporades</h2><p>Temporada 2025/2026</p>
<h3>TDM G1</h3><a href="https://fctt.cat/lligues/grup-1-2/"></a>
<h2>Copa catalana femenina</h2>
<h3>1a Divisi&oacute;</h3><a href="https://fctt.cat/lligues/1a-divisio-2/"></a>
<h2>Altres temporades</h2><p>Temporada 2025/2026</p>
<h3>1a Divisió</h3><a href="https://fctt.cat/lligues/1a-divisio/"></a>
<h2>Lligues estatals (grups catalans)</h2>
<h3>SUF</h3><a href="https://fctt.cat/lligues/suf/"></a>
</body></html>"""


def player(code, name):
    return f'<a href="?codi_jugador={code}&#038;jugador_nom=X" class="br-apic-player-link">{name}</a>'


def match_html(day, home_id, away_id, score="- - -", table=True, acta=True, unlinked=False):
    played = score != "- - -"
    rows = ""
    if played and table:
        away_player = "SENSE ENLLAÇ, PERE" if unlinked else player(away_id * 10, "VISITANT, PERE")
        rows = ('<table class="match-results-table"><thead><tr><th></th></tr></thead><tbody><tr>'
                f'<td class="position">A</td><td class="player-info">{player(home_id * 10, "LOCAL, JOAN")}</td>'
                f'<td class="position">X</td><td class="player-info">{away_player}</td>'
                '<td class="game">11-5</td><td class="result">3 - 0</td></tr><tr>'
                f'<td class="player-info">{player(1, "DOBLE, U")}<br>{player(2, "DOBLE, DOS")}</td>'
                f'<td class="player-info">{player(3, "DOBLE, TRES")}<br>{player(4, "DOBLE, QUATRE")}</td>'
                '<td class="game">11-9</td></tr></tbody></table>')
    acta_link = (f'<div class="acta-info"><a href="https://control.fctt.cat/partido/{home_id}{away_id}/imprimir/acta"'
                 ' class="acta-link">Descarregar acta</a></div>') if played and acta else ""
    return (f'<div class="match-container"><div class="match-datetime">{day} · 17:00</div>'
            f'<div class="match-teams-row"><div class="match-teams">'
            f'<div class="team-home"><a href="?team_name_id={home_id}">EQUIP {home_id}</a></div>'
            f'<div class="match-score">{score}</div>'
            f'<div class="team-away"><a href="?team_name_id={away_id}">EQUIP {away_id}</a></div></div></div>'
            f'{rows}<div class="match-info"><div class="field-info"><strong>Terreny de joc:</strong> PAVELLÓ {home_id}'
            f'</div>{acta_link}</div></div>')


def page(sections, *, selector=(), heading="Tercera Divisió Masculina Grup 1 - Temporada 2026/2027"):
    buttons = "".join(f'<button class="jornada-btn" data-jornada="{n}">{n}</button>' for n in selector)
    body = "".join(f'<h2 class="jornada-results-title"><a href="/lligues/grup-1/?jornada={n}">Jornada {n}</a></h2>'
                   + "".join(matches) for n, matches in sections.items())
    return (f"<!DOCTYPE html><html><head><title>TDM G1</title></head><body><main><h2>{heading}</h2>"
            f"{buttons}{body}</main></body></html>").encode("utf-8")


NO_RESULTS = (b"<!DOCTYPE html><html><head><title>TDM G1</title></head><body><main>"
              b"<p>No s'han trobat resultats.</p><p>No s'han trobat partits.</p></main></body></html>")
PDF = b"%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n"
J1_PLAYED = [match_html("26/09/2026", 1, 2, "4 - 2"), match_html("26/09/2026", 3, 4, "6 - 0", table=False, acta=False)]
J1_CALENDAR = [match_html("26/09/2026", 1, 2, "4 - 2", table=False), match_html("26/09/2026", 3, 4, "6 - 0",
                                                                                    table=False, acta=False)]
J2_FUTURE = [match_html("17/10/2026", 2, 3), match_html("18/10/2026", 4, 1)]
GROUP_PAGE = page({1: J1_CALENDAR, 2: J2_FUTURE}, selector=(1, 2))


class FakeResponse(io.BytesIO):
    def __init__(self, body, content_type="text/html; charset=UTF-8"):
        super().__init__(body)
        self.status = 200
        self.headers = {"Content-Type": content_type}


class FakeOpener:
    """Serve queued responses per URL; the last queued response repeats."""

    def __init__(self, routes):
        self.routes = {url: list(responses) for url, responses in routes.items()}
        self.calls = []

    def open(self, request, timeout):
        url = request.full_url
        self.calls.append((url, request.get_header("User-agent")))
        queue = self.routes.get(url)
        if not queue:
            raise HTTPError(url, 404, "Not Found", Message(), None)
        response = queue.pop(0) if len(queue) > 1 else queue[0]
        if isinstance(response, int):
            raise HTTPError(url, response, "Error", Message(), None)
        if isinstance(response, tuple):
            return FakeResponse(*response)
        return FakeResponse(response)


def group_url(jornada=None):
    return "https://fctt.cat/lligues/grup-1/" + (f"?jornada={jornada}" if jornada else "")


class Harness:
    def __init__(self, tmp, routes, **options):
        self.tmp = Path(tmp)
        self.metrics = dl.MetricsStore(self.tmp / "metrics.json")
        self.opener = FakeOpener({dl.INDEX_URL: [INDEX_HTML.encode("utf-8")], **routes})
        self.options = dl.Options(output_dir=self.tmp / "out", groups={"g1"}, **options)

    def run(self):
        self.opener.calls.clear()
        tuning = self.metrics.tuning()
        client = dl.HttpClient(self.metrics, request_delay=0, backoff_base=0, sleep=lambda _: None, opener=self.opener)
        self.metrics.start_run({}, tuning)
        ok = dl.IncrementalDownloader(client, self.metrics, self.options, tuning, now=lambda: NOW).run()
        return ok, self.metrics.finish_run()

    def path(self, jornada):
        return self.tmp / "out" / "2026-2027" / "tdm" / "g1" / "regular" / f"jornada-{jornada}.html"

    def calls_to(self, url):
        return [call for call in self.opener.calls if call[0] == url]


class DiscoveryTests(unittest.TestCase):
    def test_discovers_current_season_leagues_only(self):
        season, leagues = dl.discover_leagues(dl.parse_html(INDEX_HTML))
        self.assertEqual(season, "2026-2027")
        self.assertEqual([(l.gender, l.category, l.group, l.url) for l in leagues], [
            ("male", "tdm", "g1", "https://fctt.cat/lligues/grup-1/"),
            ("male", "tdm", "g2", "https://fctt.cat/lligues/grup-2-2/"),
            ("female", "copa-catalana-femenina", "1a-divisio", "https://fctt.cat/lligues/1a-divisio-2/"),
        ])
        self.assertTrue(all(l.season == "2026-2027" and l.territory == "catalunya" for l in leagues))

    def test_heading_gives_season_and_phase(self):
        heading = "Copa Catalana Femenina Grup 1 - Fase Final - Temporada 2026/2027"
        doc = dl.parse_html(page({}, heading=heading).decode("utf-8"))
        self.assertEqual(dl.page_heading_info(doc), ("2026-2027", "fase-final"))
        self.assertEqual(dl.page_heading_info(dl.parse_html(GROUP_PAGE.decode("utf-8"))), ("2026-2027", None))


class ExtractionAndValidationTests(unittest.TestCase):
    def sections(self, body):
        return dl.extract_sections(dl.parse_html(body.decode("utf-8") if isinstance(body, bytes) else body))

    def test_saved_played_page_is_complete(self):
        matches = self.sections((SAVED_PAGES / "G1" / "jornada-1.html").read_text(encoding="utf-8"))[1]
        self.assertEqual(len(matches), 6)
        first = matches[0]
        self.assertEqual((first.date, first.time, first.home_id, first.away_id, first.match_id),
                         ("2026-09-26", "16:00", "130", "149", "2991"))
        self.assertTrue(first.location.startswith("POLISPORTIU MUNICIPAL RIPOLLET"))
        self.assertEqual(first.rows[0]["players"][0], [{"code": "11774", "name": "GARCÍA LINARES, ALEX"}])
        self.assertEqual(dl.validate_matches(matches, []).status, "complete")

    def test_saved_scheduled_page_is_partial(self):
        matches = self.sections((SAVED_PAGES / "G1" / "jornada-4.html").read_text(encoding="utf-8"))[4]
        validation = dl.validate_matches(matches, [])
        self.assertEqual(validation.status, "partial")
        self.assertFalse(any(m.played for m in matches))

    def test_doubles_and_walkover(self):
        matches = self.sections(page({1: J1_PLAYED}))[1]
        self.assertEqual([p["code"] for p in matches[0].rows[1]["players"][0]], ["1", "2"])
        self.assertTrue(matches[1].walkover)
        validation = dl.validate_matches(matches, [])
        self.assertEqual(validation.status, "complete")
        self.assertIn("walkover", validation.warnings[0])

    def test_unlinked_player_is_invalid_but_salvageable(self):
        matches = self.sections(page({1: [match_html("26/09/2026", 1, 2, "4 - 2", unlinked=True)]}))[1]
        validation = dl.validate_matches(matches, [])
        self.assertEqual((validation.status, validation.salvageable), ("invalid", True))
        self.assertIn("SENSE ENLLAÇ, PERE", validation.errors[0])
        self.assertEqual(matches[0].content_status, "incomplete")

    def test_page_out_of_sync_with_calendar_is_not_salvageable(self):
        calendar = self.sections(GROUP_PAGE)[1]
        stale = self.sections(page({1: [match_html("26/09/2026", 1, 2), match_html("26/09/2026", 3, 4)]}))[1]
        validation = dl.validate_matches(stale, calendar)
        self.assertEqual((validation.status, validation.salvageable), ("invalid", False))
        missing = dl.validate_matches(self.sections(page({1: J1_PLAYED[:1]}))[1], calendar)
        self.assertIn("expected 2 matches", missing.errors[0])

    def test_is_future(self):
        calendar = self.sections(GROUP_PAGE)
        self.assertFalse(dl.is_future(calendar[1], NOW))
        self.assertTrue(dl.is_future(calendar[2], NOW))
        self.assertFalse(dl.is_future([], NOW))

    def test_match_day_argument(self):
        self.assertEqual(dl.parse_match_days("1,3-5"), {1, 3, 4, 5})
        self.assertEqual(dl.parse_filter("G1, 1a Divisió"), {"g1", "1a-divisio"})
        self.assertIsNone(dl.parse_filter("all"))


class DownloaderTests(unittest.TestCase):
    def test_incremental_runs(self):
        with tempfile.TemporaryDirectory() as tmp:
            harness = Harness(tmp, {
                group_url(): [GROUP_PAGE],
                group_url(1): [NO_RESULTS, 500, page({1: J1_PLAYED})],
                group_url(2): [NO_RESULTS],
            })
            ok, run = harness.run()
            self.assertTrue(ok)
            # Past match day: "no results" and HTTP 500 are retried with a different user agent each time.
            agents = [agent for _, agent in harness.calls_to(group_url(1))]
            self.assertEqual(len(agents), 3)
            self.assertEqual(len(set(agents)), 3)
            saved = dl.read_saved_meta(harness.path(1))
            self.assertEqual(saved["acta-content-status"], "complete")
            text = harness.path(1).read_text(encoding="utf-8")
            self.assertIn('data-acta-content-status="complete" data-match-id="12"', text)
            # Future match day without results: one request, minimal info from the calendar.
            self.assertEqual(len(harness.calls_to(group_url(2))), 1)
            future = dl.read_saved_meta(harness.path(2))
            self.assertEqual((future["acta-content-status"], future["acta-content-origin"]), ("partial", "calendar"))
            minimal = dl.extract_sections(dl.parse_html(harness.path(2).read_text(encoding="utf-8")))[2]
            self.assertEqual([(m.date, m.time, m.home, m.away, m.location) for m in minimal][0],
                             ("2026-10-17", "17:00", "EQUIP 2", "EQUIP 3", "PAVELLÓ 2"))
            self.assertEqual(run["outcomes"], {"downloaded": 1, "saved_minimal": 1})
            self.assertEqual(run["counters"]["retries"], 2)

            # Second run: nothing changed, so only the index and group page are requested.
            _, run = harness.run()
            self.assertEqual([url for url, _ in harness.opener.calls],
                             ["https://fctt.cat/robots.txt", dl.INDEX_URL, group_url()])
            self.assertEqual(run["outcomes"], {"up_to_date": 2})

            metrics = json.loads((Path(tmp) / "metrics.json").read_text(encoding="utf-8"))
            self.assertEqual(metrics["totals"]["runs"], 2)
            self.assertEqual(metrics["requests"]["errors_by_kind"], {"no_results": 1, "http_500": 1})
            self.assertEqual(metrics["requests"]["attempts_until_success"]["3"], 1)
            history = metrics["match_days"]["2026-2027/tdm/g1/regular/jornada-1"]["history"]
            self.assertEqual([(h["outcome"], h["retries"]) for h in history], [("downloaded", 2), ("up_to_date", 0)])

    def test_downloaded_future_match_day_is_not_refreshed_without_force(self):
        with tempfile.TemporaryDirectory() as tmp:
            moved = page({1: J1_CALENDAR, 2: [match_html("24/10/2026", 2, 3), J2_FUTURE[1]]}, selector=(1, 2))
            harness = Harness(tmp, {group_url(): [GROUP_PAGE], group_url(1): [page({1: J1_PLAYED})],
                                    group_url(2): [page({2: J2_FUTURE})]})
            harness.run()
            saved = harness.path(2).read_bytes()
            harness.opener.routes[group_url()] = [moved]
            harness.opener.routes[group_url(2)] = [NO_RESULTS]
            # The calendar moved the future match day, but a version is already saved: no request, no rewrite.
            _, run = harness.run()
            self.assertEqual(run["outcomes"], {"up_to_date": 2})
            self.assertEqual(harness.calls_to(group_url(2)), [])
            self.assertEqual(harness.path(2).read_bytes(), saved)

    def test_force_refreshes_a_downloaded_future_match_day(self):
        with tempfile.TemporaryDirectory() as tmp:
            moved = page({1: J1_CALENDAR, 2: [match_html("24/10/2026", 2, 3), J2_FUTURE[1]]}, selector=(1, 2))
            harness = Harness(tmp, {group_url(): [GROUP_PAGE], group_url(1): [page({1: J1_PLAYED})],
                                    group_url(2): [page({2: J2_FUTURE})]})
            harness.run()
            harness.options.force = True
            harness.opener.routes[group_url()] = [moved]
            harness.opener.routes[group_url(2)] = [NO_RESULTS]
            harness.run()
            self.assertEqual(len(harness.calls_to(group_url(2))), 1)
            minimal = dl.extract_sections(dl.parse_html(harness.path(2).read_text(encoding="utf-8")))[2]
            self.assertEqual(minimal[0].date, "2026-10-24")

    def test_incomplete_actas_are_kept_as_partial(self):
        with tempfile.TemporaryDirectory() as tmp:
            unlinked = page({1: [match_html("26/09/2026", 1, 2, "4 - 2", unlinked=True), J1_PLAYED[1]]})
            harness = Harness(tmp, {group_url(): [GROUP_PAGE], group_url(1): [unlinked],
                                    group_url(2): [page({2: J2_FUTURE})]}, match_days={1}, retries=2)
            _, run = harness.run()
            self.assertEqual(len(harness.calls_to(group_url(1))), 3)
            self.assertEqual(run["outcomes"], {"incomplete": 1})
            self.assertEqual(run["counters"]["failed_downloads"], 1)
            self.assertEqual(dl.read_saved_meta(harness.path(1))["acta-matches-incomplete"], "1")
            self.assertIn('data-acta-content-status="incomplete"', harness.path(1).read_text(encoding="utf-8"))

    def test_failed_past_match_day_is_skipped_and_becomes_chronic(self):
        with tempfile.TemporaryDirectory() as tmp:
            harness = Harness(tmp, {group_url(): [GROUP_PAGE], group_url(1): [503]}, match_days={1}, retries=2)
            for _ in range(dl.CHRONIC_FAILURES):
                _, run = harness.run()
                self.assertEqual(run["outcomes"], {"failed": 1})
                self.assertFalse(harness.path(1).exists())
            self.assertEqual(run["counters"]["skipped_match_days"], 1)
            tuning = harness.metrics.tuning()
            self.assertIn("2026-2027/tdm/g1/regular/jornada-1", tuning.chronic_match_days)
            self.assertGreater(tuning.delay_factor, 1.0)
            self.assertGreater(tuning.backoff_factor, 1.0)
            harness.run()
            self.assertEqual(len(harness.calls_to(group_url(1))), 2)  # retry budget reduced to 1

    def test_future_match_day_unreachable_saves_calendar_info(self):
        with tempfile.TemporaryDirectory() as tmp:
            harness = Harness(tmp, {group_url(): [GROUP_PAGE], group_url(2): [500]}, match_days={2}, retries=1)
            _, run = harness.run()
            self.assertEqual(run["outcomes"], {"failed": 1})
            self.assertEqual(dl.read_saved_meta(harness.path(2))["acta-content-origin"], "calendar")

    def test_league_without_calendar_is_not_started(self):
        with tempfile.TemporaryDirectory() as tmp:
            harness = Harness(tmp, {group_url(): [NO_RESULTS]})
            _, run = harness.run()
            self.assertEqual(len(harness.calls_to(group_url())), 1)
            self.assertEqual(harness.metrics.data["leagues"]["2026-2027/tdm/g1"]["last_outcome"], "not_started")

    def test_unreachable_index_skips_everything(self):
        with tempfile.TemporaryDirectory() as tmp:
            harness = Harness(tmp, {}, retries=1)
            harness.opener.routes[dl.INDEX_URL] = [502]
            ok, _ = harness.run()
            self.assertFalse(ok)
            self.assertEqual(len(harness.calls_to(dl.INDEX_URL)), 2)

    def test_robots_txt(self):
        with tempfile.TemporaryDirectory() as tmp:
            robots = "https://fctt.cat/robots.txt"
            harness = Harness(tmp, {robots: [b"User-agent: *\nDisallow: /lligues/\n"], group_url(): [GROUP_PAGE]})
            harness.run()
            self.assertEqual(harness.calls_to(group_url()), [])  # disallowed: never requested
            # Unreachable robots.txt: the cached copy from the previous run still applies.
            harness.opener.routes[robots] = [500]
            harness.run()
            self.assertEqual(len(harness.calls_to(robots)), 3)
            self.assertEqual(harness.calls_to(group_url()), [])
            # Unreachable and never seen: full disallow, even the index.
            harness.metrics.data["robots"].clear()
            ok, _ = harness.run()
            self.assertFalse(ok)
            self.assertEqual(harness.calls_to(dl.INDEX_URL), [])

    def test_pdf_actas(self):
        with tempfile.TemporaryDirectory() as tmp:
            harness = Harness(tmp, {
                group_url(): [GROUP_PAGE], group_url(1): [page({1: J1_PLAYED})],
                "https://control.fctt.cat/partido/12/imprimir/acta": [(PDF, "application/pdf")],
            }, match_days={1}, want_pdf=True)
            _, run = harness.run()
            pdf = harness.path(1).with_suffix("") / "12.pdf"
            self.assertEqual(pdf.read_bytes(), PDF)
            self.assertEqual(run["counters"]["pdf_downloaded"], 1)  # the walkover has no acta
            _, run = harness.run()
            self.assertEqual(run["counters"]["pdf_skipped"], 1)


if __name__ == "__main__":
    unittest.main()
