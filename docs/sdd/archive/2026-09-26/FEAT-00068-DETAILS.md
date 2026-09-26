# Build Plan
1. Extractor (external repo `C:\git\fctt-extract`), `src/actas-html/parse_actas.py`
   `parse_match`: after parsing the result rows, detect when the last
   `marcador_acumulado` mirrors `resultado_final.marcador_partidos` (non-tie).
   In that case swap `alineaciones`, `dobles` and every `partidos[]` entry
   (`local`/`visitante` participants, `sets`, `resultado_juegos`, `ganador`,
   `marcador_acumulado`) via `swap_sides`/`swap_score`, keeping `cruce` as
   written (A/B/C letter first, matching RFETM). Emit `abc_es_local` from the
   detected orientation instead of hardcoding `True`.
2. Extractor tests (`src/actas-html/test_parse_actas.py`, `OrientationTests`):
   pin jornada 22 G2 (TERRASSA home, A/B/C = IGUALADA) as re-oriented and the
   other matches of that page as unchanged, against the real HTML input under
   `resources/actas-html`.
3. Importer guard (this repo), new
   `tt-data-league-import/.../fctt/process/FcttActaOrientation.java`: the same
   mirror detection and swap on the parsed `Acta`, applied at the start of
   `FcttMatchImportProcessor.process` so stale or future mis-oriented FCTT
   files still store lineups, games and doubles against the real teams. A
   correctly oriented acta passes through unchanged.
4. Importer test (`FcttImportProcessorsTest`) with fixture
   `src/test/resources/actas/acta_fctt_abc_away.json` (the real stale
   jornada-22 acta): lineups A/B/C → CPP IGUALADA, X/Y/Z → TERRASSA; game
   players, sets, winner sides and running score re-oriented; doubles pairs on
   the right side; match score and winner unchanged (4-3 TERRASSA).
5. Data: regenerate `resources/actas-json` with the fixed extractor and
   re-import FCTT so already stored matches are corrected (requires user
   go-ahead; affects shared local data).
6. Verify on `/partits/d9e66696-0d2f-4601-a6e1-6362752409a2` (or the new id
   after re-import) that each team panel lists its own players.

# Implementation Guidelines

- Fix orientation at the source (extractor) and keep the importer guard as a
  defensive, no-op-when-correct check; do not change the web read model or
  the stored `resultado_final`-based score and winner, which were correct.
- Scope is FCTT: RFETM actas are already oriented by real side (20,045
  consistent, 504 ties, 0 mirrored). BCNESA's extractor also hardcodes
  `abc_es_local: True`, but its `marcador_partidos` is not a games tally, so
  mirror detection does not apply there — tracked as a follow-up, not changed
  here.

# Notes

- Reproduced on match `d9e66696-0d2f-4601-a6e1-6362752409a2` (FCTT, tercera
  nacional G2, jornada 22, "CTT ELS AMICS TERRASSA 4 – 3 CPP IGUALADA"), raw
  acta `C:\git\fctt-extract\resources\actas-json\2025-2026\tercera nacional\G2\jornada_22_partido_2736.json`.
- A first attempt (deriving the header score from game winners at read time)
  was wrong and has been reverted: the stored 4-3 score was correct; the
  lineups and games were the part attributed to the wrong sides.
- **Root cause (confirmed)**: the external FCTT extractor
  (`C:\git\fctt-extract\src\actas-html\parse_actas.py`, `parse_match`) treats
  the first player column of each result-table row as `local`, but the FCTT
  page always lists the A/B/C column first regardless of which team is home.
  It also hardcodes `"abc_es_local": True`. So `alineaciones`, `dobles` and
  `partidos` (letters, players, set scores, `ganador`, `marcador_acumulado`)
  are oriented A/B/C-vs-X/Y/Z, while `equipos` and `resultado_final` use the
  real home/away sides. `FcttMatchImportProcessor` maps `local` → home team,
  so whenever A/B/C was actually the away team, both lineups, every game and
  the doubles pairs are attached to the wrong team.
- Evidence: player season histories show FCTT players filed under the
  opposing club in roughly half their matches (e.g. Luco Pérez and Troncoso,
  Igualada regulars, appear under the opponent in rounds 3/8/12/15/22).
- Scope across all 396 FCTT 2025-2026 actas: 197 consistent (last
  `marcador_acumulado` equals `resultado_final.marcador_partidos`), 197 exact
  mirror swaps (A/B/C was the away team), 0 ambiguous, 2 without games. A
  mirror comparison therefore detects orientation unambiguously for this
  dataset.
- Decision (user): fix both the extractor and the importer.
- Validation:
  - Extractor raw output (before fix) over all 396 FCTT HTML inputs: 197
    consistent, 197 mirrored, 2 without games. After fix: 394 consistent,
    0 mirrored, 2 without games; `abc_es_local` false for 197, and the flag
    agrees with the lineup letters in every record.
  - `python -m unittest test_parse_actas.OrientationTests`: 2 passed. The
    pre-existing `ParseActasTests` point at `src/actas-html/resources`, which
    does not exist (inputs live in the repo-level `resources/actas-html`), so
    they fail independently of this change.
  - `FcttImportProcessorsTest.attachesLineupsGamesAndDoublesToTheRealSidesWhenTheAbcColumnWasTheAwayTeam`
    passes. The full `tt-data-league-import` suite has 8 failures that
    predate this work, all "expected N clubs/players but was 0" in
    `ImportProcessorsTest`, `BcnesaImportProcessorsTest`,
    `FcttImportProcessorsTest.storesClubsAndPlayersUnderTheFcttSource` and
    `TeamToClubConsolidationProcessorTest`; none touch FCTT match storage.
- Scan of other sources: RFETM actas are oriented by real side (20,045
  consistent, 504 ties, 0 mirrored, 63 without games, 7 other). BCNESA's
  extractor also hardcodes `abc_es_local: True`, but its
  `marcador_partidos` is not a games tally (e.g. final 18-7 vs last running
  6-0), so mirror detection cannot tell whether it is affected.
- Follow-ups (not in scope):
  - BCNESA orientation needs a different signal to verify.
  - `MatchActaDialog` positions rows from `crossover`, which keeps the A/B/C
    letter first; when A/B/C is the away team (all RFETM `abc_es_local:
    false` matches today, and re-oriented FCTT matches after re-import) the
    away letter is shown next to the home player.
  - The broken `ParseActasTests` resource path and the 8 pre-existing
    import-test failures.
- Pending: regenerate `resources/actas-json` with the fixed extractor,
  re-import FCTT, and verify the Match details page.
