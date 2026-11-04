import importlib.util
import json
import logging
import sys
import tempfile
import unittest
from pathlib import Path


from ingest_fctt import parse as pa
pa.LOGGER.addHandler(logging.NullHandler())
pa.LOGGER.propagate = False

REAL_CONTENT = Path(__file__).resolve().parent / "fixtures" / "content"

HOME = ("130", "CLINICA DENTAL RIPOLLET")
AWAY = ("149", "CTT MOLINS DE REI")
HOME_PLAYERS = {"A": ("11774", "GARCIA, ALEX"), "B": ("14724", "PARDO, LUCAS"), "C": ("10912", "SILVA, ADRIAN")}
AWAY_PLAYERS = {"X": ("13765", "ARAUJO, ISMAEL"), "Y": ("2259", "CAYMEL, ISMAEL"), "Z": ("790", "CUENCA, OSCAR")}
# (first column letter, second column letter, games of the first column player, result, running score)
GAMES = [
    ("A", "Y", ["11-5", "11-7", "5-11", "11-7"], "3 - 1", "1 - 0"),
    ("B", "X", ["7-11", "5-11", "6-11"], "0 - 3", "1 - 1"),
    ("C", "Z", ["9-11", "3-11", "12-10", "8-11"], "1 - 3", "1 - 2"),
    ("A", "X", ["5-11", "3-11", "10-12"], "0 - 3", "1 - 3"),
    ("C", "Y", ["7-11", "8-11", "11-8", "7-11"], "1 - 3", "1 - 4"),
    ("B", "Z", ["11-5", "11-4", "10-12", "6-11", "11-3"], "3 - 2", "2 - 4"),
]


def player_link(code, name):
    return f'<a href="?codi_jugador={code}&#038;jugador_nom=x&#038;jornada=1" class="br-apic-player-link">{name}</a>'


def team_div(cls, team):
    return f'<div class="{cls}"><a href="?team_name_id={team[0]}">{team[1]}</a></div>'


def played_match(*, home_first=True, status="complete", score="2 - 4", games=GAMES, extra_rows=""):
    first, second = (HOME, AWAY) if home_first else (AWAY, HOME)
    players = {**HOME_PLAYERS, **AWAY_PLAYERS}
    rows = []
    for left, right, sets, result, running in games:
        cells = "".join(f'<td class="game">{value}</td>' for value in sets + [""] * (5 - len(sets)))
        rows.append(
            f'<tr><td class="position">{left}</td><td class="player-info">{player_link(*players[left])}</td>'
            f'<td class="position">{right}</td><td class="player-info">{player_link(*players[right])}</td>'
            f'{cells}<td class="result">{result}</td><td class="global">{running}</td></tr>')
    return (
        f'<div class="match-container" data-acta-content-status="{status}" data-match-id="2991">'
        f'<div class="match-datetime">26/09/2026 · 16:00</div><div class="match-teams-row"><div class="match-teams">'
        f'{team_div("team-home", HOME)}<div class="match-score">{score}</div>{team_div("team-away", AWAY)}</div></div>'
        f'<table class="match-results-table"><thead><tr><th></th><th>{first[1]}</th><th></th><th>{second[1]}</th>'
        f'<th>J1</th><th>J2</th><th>J3</th><th>J4</th><th>J5</th><th>Res.</th><th>Global</th></tr></thead>'
        f'<tbody>{"".join(rows)}{extra_rows}</tbody></table>'
        f'<div class="match-info"><div class="field-info"><strong>Terreny de joc:</strong> POLISPORTIU. C/ MAGALLANES, 22,08291 RIPOLLET</div>'
        f'<div class="referee-info"><strong>Àrbitre:</strong> ADRIÀ HERNÁNDEZ</div>'
        f'<div class="acta-info"><a href="https://control.fctt.cat/partido/2991/imprimir/acta" class="acta-link">Descarregar acta</a></div></div></div>')


def scheduled_match(home=HOME, away=AWAY, status="partial", score="- - -"):
    return (
        f'<div class="match-container" data-acta-content-status="{status}" data-match-id="{home[0]}-{away[0]}">'
        f'<div class="match-datetime">03/10/2026 · 17:30</div><div class="match-teams-row"><div class="match-teams">'
        f'{team_div("team-home", home)}<div class="match-score">{score}</div>{team_div("team-away", away)}</div></div>'
        f'<div class="match-info"><div class="field-info"><strong>Terreny de joc:</strong> GIMNÀS. C/ MAJOR 1,08750 MOLINS DE REI</div></div></div>')


def page(matches, *, status="complete", title=True, gender="male", jornada=1):
    league = ('<h2 class="br-apic-league-title">Tercera Divisió Masculina Grup 1 - Temporada 2026/2027</h2>'
              if title else "")
    return (
        f'<!DOCTYPE html><html><head><meta name="acta-content-status" content="{status}">'
        f'<meta name="acta-gender" content="{gender}"><meta name="acta-match-day" content="{jornada}">'
        f'<meta name="acta-matches" content="{len(matches)}"><title>TDM G1 - Jornada {jornada}</title></head><body>'
        f'{league}<h2 class="jornada-results-title"><a href="?jornada={jornada}">Jornada {jornada}</a></h2>'
        f'{"".join(matches)}<style>.match-results-table {{}}</style></body></html>')


class ParserTestCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.input_dir = self.root / "content"
        self.output_dir = self.root / "json"
        self.validator = pa.SchemaValidator(pa.DEFAULT_SCHEMA)

    def tearDown(self):
        self.tmp.cleanup()

    def write_page(self, html, *, category="tdm", group="g1", phase="regular", jornada=1, season="2026-2027"):
        path = self.input_dir / season / category / group / phase / f"jornada-{jornada}.html"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(html, encoding="utf-8")
        return path

    def parse(self, html, **location):
        self.write_page(html, **location)
        pages, _ = pa.discover_pages(self.input_dir, seasons=None, categories=None, groups=None, phases=None,
                                     match_days=None)
        return pa.parse_page(pages[-1], pages[-1].path.read_text(encoding="utf-8"), pa.CompetitionNames())

    def run_parser(self, **overrides):
        options = pa.Options(input_dir=self.input_dir, output_dir=self.output_dir, **overrides)
        runner = pa.IncrementalParser(options)
        return runner.run(), runner.counters

    def assertValid(self, record):
        if self.validator.validator is None:
            self.skipTest("jsonschema not installed")
        self.assertEqual(self.validator.errors(record), [])


class CompleteActaTest(ParserTestCase):
    def test_full_acta_home_team_first(self):
        result = self.parse(page([played_match()]))
        self.assertEqual(result.errors, [])
        match = result.matches[0]
        record = match.record
        self.assertTrue(match.published)
        self.assertEqual(match.filename, "jornada_1_local_team_130_away_team_149.json")
        self.assertEqual(record["id_partido"], "2026-2027_tdm_g1_regular_130-149_1")
        self.assertEqual((record["temporada"], record["genero"], record["fase"], record["grupo"], record["jornada"]),
                         ("2026/2027", "masculino", "1a Fase", 1, 1))
        self.assertEqual(record["competicion"], "Tercera Divisió Masculina Grup 1")
        self.assertEqual((record["fecha"], record["hora"]), ("2026-09-26", "16:00"))
        self.assertEqual(record["lugar"]["ciudad"], "RIPOLLET")
        self.assertTrue(record["abc_es_local"])
        self.assertEqual(sorted(record["alineaciones"]["local"]), ["A", "B", "C"])
        self.assertEqual(record["alineaciones"]["visitante"]["Z"], {"nombre": "CUENCA, OSCAR", "licencia": "790", "id": "790"})
        self.assertEqual(record["arbitros"]["principal"], {"nombre": "ADRIÀ HERNÁNDEZ", "licencia": None})
        first = record["partidos"][0]
        self.assertEqual(first["cruce"], "A vs Y")
        self.assertEqual(first["sets"][0], {"set": 1, "local": 11, "visitante": 5})
        self.assertEqual((first["resultado_juegos"], first["ganador"]), ({"local": 3, "visitante": 1}, "local"))
        self.assertEqual(record["partidos"][-1]["marcador_acumulado"], {"local": 2, "visitante": 4})
        self.assertEqual(record["resultado_final"], {
            "ganador": AWAY[1], "marcador_partidos": {"local": 2, "visitante": 4},
            "marcador_juegos": {"local": 8, "visitante": 15}})
        self.assertEqual(match.warnings, [])
        self.assertValid(record)

    def test_away_team_first_in_results_table_is_swapped(self):
        # Same games, but the table lists the away team first: its letters are A/B/C.
        result = self.parse(page([played_match(home_first=False, score="4 - 2")]))
        record = result.matches[0].record
        self.assertFalse(record["abc_es_local"])
        self.assertEqual(sorted(record["alineaciones"]["local"]), ["X", "Y", "Z"])
        self.assertEqual(sorted(record["alineaciones"]["visitante"]), ["A", "B", "C"])
        first = record["partidos"][0]
        self.assertEqual(first["cruce"], "Y vs A")
        self.assertEqual(first["sets"][0], {"set": 1, "local": 5, "visitante": 11})
        self.assertEqual(first["ganador"], "visitante")
        self.assertEqual(record["partidos"][-1]["marcador_acumulado"], {"local": 4, "visitante": 2})
        self.assertEqual(record["resultado_final"]["ganador"], HOME[1])
        self.assertValid(record)

    def test_undecided_games_and_doubles(self):
        games = GAMES[:4] + [("C", "Y", [], "", "1 - 3")]
        doubles = (f'<tr><td class="position">Db</td><td class="player-info">{player_link("11774", "GARCIA, ALEX")}<br>'
                   f'{player_link("14724", "PARDO, LUCAS")}</td><td class="position">Db</td><td class="player-info">'
                   f'{player_link("13765", "ARAUJO, ISMAEL")}<br>{player_link("2259", "CAYMEL, ISMAEL")}</td>'
                   f'<td class="game">11-9</td><td class="game">11-9</td><td class="game">11-9</td><td class="game"></td>'
                   f'<td class="game"></td><td class="result">3 - 0</td><td class="global">2 - 3</td></tr>')
        record = self.parse(page([played_match(score="2 - 3", games=games, extra_rows=doubles)])).matches[0].record
        skipped = record["partidos"][4]
        self.assertTrue(skipped["no_disputado"])
        self.assertEqual((skipped["sets"], skipped["resultado_juegos"], skipped["ganador"]), ([], None, None))
        self.assertEqual(skipped["motivo"], "Partido no disputado")
        doubles_game = record["partidos"][5]
        self.assertEqual(doubles_game["tipo"], "dobles")
        self.assertEqual(doubles_game["local"]["letra"], "Db")
        self.assertEqual([player["licencia"] for player in record["dobles"]["local"]], ["11774", "14724"])
        self.assertValid(record)

    def test_complete_status_without_table_is_kept_unpublished(self):
        match = self.parse(page([scheduled_match(status="complete", score="6 - 0")])).matches[0]
        self.assertFalse(match.published)
        self.assertEqual(match.record["resultado_final"]["marcador_partidos"], {"local": 6, "visitante": 0})
        self.assertIn("walkover", match.warnings[0])
        self.assertValid(match.record)


class PartialActaTest(ParserTestCase):
    def test_scheduled_match_has_minimum_info_only(self):
        result = self.parse(page([scheduled_match(), played_match()], status="partial"))
        scheduled, played = result.matches
        record = scheduled.record
        self.assertFalse(record["acta_publicada"])
        self.assertEqual((record["fecha"], record["hora"]), ("2026-10-03", "17:30"))
        self.assertEqual(record["equipos"]["local"]["nombre"], HOME[1])
        self.assertEqual(record["lugar"]["ciudad"], "MOLINS DE REI")
        self.assertEqual((record["partidos"], record["alineaciones"]), ([], {"local": {}, "visitante": {}}))
        self.assertIsNone(record["resultado_final"]["marcador_partidos"])
        self.assertValid(record)
        # A match marked complete on a partial page is still parsed in full.
        self.assertTrue(played.published)

    def test_incomplete_match_keeps_final_score(self):
        record = self.parse(page([played_match(status="incomplete")], status="partial")).matches[0].record
        self.assertFalse(record["acta_publicada"])
        self.assertEqual(record["partidos"], [])
        self.assertEqual(record["resultado_final"]["ganador"], AWAY[1])
        self.assertValid(record)

    def test_page_status_is_used_without_match_status(self):
        html = page([played_match().replace(' data-acta-content-status="complete"', "")], status="partial")
        self.assertTrue(self.parse(html).matches[0].published)
        html = page([scheduled_match().replace(' data-acta-content-status="partial"', "")], status="partial")
        self.assertFalse(self.parse(html).matches[0].published)

    def test_calendar_page_takes_competition_from_sibling_pages(self):
        self.write_page(page([played_match()]), jornada=1)
        result = self.parse(page([scheduled_match()], status="partial", title=False, jornada=2), jornada=2)
        self.assertEqual(result.matches[0].record["competicion"], "Tercera Divisió Masculina Grup 1")

    def test_page_without_matches_is_an_error(self):
        result = self.parse(page([], status="partial").replace("<body>", "<body>No s'han trobat resultats."))
        self.assertEqual(result.matches, [])
        self.assertIn("No s'han trobat resultats", result.errors[0])


class IncrementalRunTest(ParserTestCase):
    def test_outputs_are_kebab_case_and_runs_are_incremental(self):
        source = self.write_page(page([scheduled_match()], status="partial"), category="Vet 1a", group="Group A",
                                 phase="Play Off Títol")
        ok, counters = self.run_parser()
        self.assertTrue(ok)
        folder = self.output_dir / "2026-2027" / "vet-1a" / "group-a" / "play-off-titol"
        output = folder / "jornada_1_local_team_130_away_team_149.json"
        data = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual((data["fase"], data["grupo"]), ("Play Off Títol", 0))
        self.assertEqual(counters["json_written"], 1)

        ok, counters = self.run_parser()
        self.assertEqual((counters["pages_unchanged"], counters["json_written"]), (1, 0))

        # The pairing changes: the new JSON replaces the old one.
        source.write_text(page([scheduled_match(away=("152", "CTT HOSPITALET"))], status="partial"), encoding="utf-8")
        ok, counters = self.run_parser()
        self.assertEqual((counters["json_written"], counters["json_removed"]), (1, 1))
        self.assertFalse(output.exists())
        self.assertTrue((folder / "jornada_1_local_team_130_away_team_152.json").exists())

    def test_the_run_reports_its_page_count_and_each_finished_page(self):
        from ingest_common import progress
        from ingest_common.run import IngestStage, NoOpListener

        class Recorder(NoOpListener):
            def __init__(self):
                self.events = []

            def stage_total(self, stage, total):
                self.events.append(("total", total))

            def item_processed(self, stage, item):
                self.events.append(("item", item))

        self.write_page(page([played_match()]), group="g1")
        self.write_page(page([played_match()]), group="g2")
        recorder = Recorder()

        with progress.bind(recorder, IngestStage.PARSE):
            ok, _ = self.run_parser()

        self.assertTrue(ok)
        self.assertEqual(recorder.events[0], ("total", 2))
        self.assertEqual(len([event for event in recorder.events if event[0] == "item"]), 2)

    def test_dry_run_writes_nothing_and_filters_apply(self):
        self.write_page(page([played_match()]), group="g1")
        self.write_page(page([played_match()]), group="g2")
        ok, counters = self.run_parser(dry_run=True, groups={"g2"})
        self.assertTrue(ok)
        self.assertEqual((counters["pages"], counters["json_written"]), (1, 1))
        self.assertFalse(self.output_dir.exists())

    def test_cli_returns_error_without_input(self):
        log = self.root / "parse.log"
        self.assertEqual(pa.main(["--input_dir", str(self.root / "missing"), "--output_dir", str(self.root / "out"),
                                  "--log_file", str(log)]), 2)
        pa.LOGGER.handlers[:] = [logging.NullHandler()]


class RealContentTest(ParserTestCase):
    def test_downloaded_pages_follow_the_acta_model(self):
        pages, _ = pa.discover_pages(REAL_CONTENT, seasons=None, categories=None, groups=None, phases=None,
                                     match_days=None)
        competitions = pa.CompetitionNames()
        for match_day in pages[:30]:
            result = pa.parse_page(match_day, match_day.path.read_text(encoding="utf-8"), competitions)
            self.assertEqual(result.errors, [], match_day.key)
            for match in result.matches:
                with self.subTest(file=match.filename, page=match_day.key):
                    self.assertValid(match.record)


if __name__ == "__main__":
    unittest.main()
