# Build Plan
Source task: **T1** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.1; gaps G14, G17, G18, G20).

## Acceptance Criteria
- [x] ActaCompleteness { PLAYED, PENDING, PARTIAL, INVALID } and a shared classifier over Acta exist in tt-data-league-import
- [x] Rules are evaluated in the order of analysis section 4.1 and resultado_final is never read to decide the class
- [x] Games with no_disputado: true count as complete; legacy acta without acta_publicada needs at least one game with a result to be PLAYED
- [x] A PENDING acta without both teams is identified as an unresolved pending fixture
- [x] A JUnit test covers each rule and each T0 fixture; the FCTT 6-0, BCNESA 4-4 and RFETM decided 0-0 fixtures classify as PENDING
- [x] A published acta is INVALID only when it has no games, no game with a result, an empty lineup side, or a null abc_es_local; the 2-player-per-side FCTT female fixture classifies as PLAYED

## Starting state (verified 2026-09-27)

- FEAT-00075 is `done`. The eleven T0 fixtures and `IncrementalActaFixturesTest` are in `tt-data-league-import/src/test/`.
- `Acta` (`tt-data-league-import/src/main/java/org/cttelsamicsterrassa/data/load/shared/parse/acta/Acta.java`) exposes `published()` as a nullable `Boolean`. `null` means the field was missing. Only `isPublished()` treats a missing value as published. `games()` is never null. `lineups()`, `teams()`, and `abcIsHome()` can be null.
- `ActaGame` exposes `sets()` (never null), `setsWon()` (`ActaScore`, nullable, sides nullable), `winner()`, and `wasNotPlayed()`.
- `ActaLineups.home()` / `away()` are never null (they default to an empty map).
- Nothing classifies actas today. `FcttMatchImportProcessor` (l.86) and `FcttPreviewValidationProcessor` (l.35) call `isPublished()` directly. They are rewired in FEAT-00081 (T6) and the preview feature (T12), not here.
- Measured content of the fixtures and the class the rules below give them:

  | Fixture | `acta_publicada` | Games (with result / `no_disputado`) | Lineup sides | Expected |
  | --- | --- | --- | --- | --- |
  | `acta_rfetm_2026_published.json` | true | 7 (7 / 0) | 3 / 3 | PLAYED |
  | `acta_rfetm_2026_unpublished.json` | false | 0 | 0 / 0 | PENDING |
  | `acta_rfetm_2025_decided_0_0.json` | missing | 7 (0 / 7) | 3 / 0 | PENDING (step 4, G17) |
  | `acta_rfetm_legacy_empty.json` | missing | 0 | 0 / 0 | PENDING (step 4) |
  | `acta_bcnesa_2026_unpublished.json` | false | 0 | 0 / 0 | PENDING |
  | `acta_bcnesa_2026_placeholder_4_4.json` | false | 0 | 0 / 0 | PENDING (step 1, G14) |
  | `acta_fctt_2026_published.json` | true | 6 (6 / 0) | 3 / 3 | PLAYED |
  | `acta_fctt_unpublished.json` | false | 0 | 0 / 0 | PENDING |
  | `acta_fctt_2026_no_team_placeholder.json` | false | 0 | 0 / 0 | PENDING, unresolved (G18) |
  | `acta_fctt_2025_placeholder_6_0.json` | false | 0 | 0 / 0 | PENDING (step 1, G14) |
  | `acta_fctt_female_groupless.json` | true | 5 (5 / 0) | **2 / 2** | PLAYED (see Notes: lineup rule) |
  | `acta_singles.json`, `acta_doubles.json`, `acta_bcnesa_xyz_home.json`, `acta_fctt_abc_away.json`, `acta_matchday.json`, `acta_doubles_matchday.json` | missing | all with results | 1–3 | PLAYED (step 5) |

## Contract

New package `org.cttelsamicsterrassa.data.load.shared.classify` in `tt-data-league-import/src/main/java/`. It sits beside `shared.parse.acta` and not inside it, because the `Acta` records deliberately apply no interpretation. No Spring or domain dependency.

```java
public enum ActaCompleteness { PLAYED, PENDING, PARTIAL, INVALID }

public record ActaClassification(
        ActaCompleteness completeness,
        String reason,                      // non-null, human-readable; used later for issue reports
        boolean unresolvedPendingFixture) { // true only when completeness == PENDING
    public boolean isPlayed() { ... }
}

public final class ActaCompletenessClassifier {
    public ActaClassification classify(Acta acta);                          // uses acta.games()
    public ActaClassification classify(Acta acta, List<ActaGame> games);   // per fixture (BCNESA after the split)
    static boolean hasResult(ActaGame game);
}
```

- The classifier is a stateless final class with a public no-arg constructor, so processors can hold it as a field (as they do with `ActaParser`). Do not make it a Spring bean.
- `classify(acta, games)` reads the game content from `games`. It reads the publication flag, `abcIsHome`, `lineups`, and `teams` from `acta`. It throws `NullPointerException` (via `Objects.requireNonNull`) for a null `acta` or `games`. There is no other failure mode: every acta gets a class.
- It must never call `acta.finalResult()` or `game.cumulativeScore()`.

## Steps

1. **Add `ActaCompleteness`** (enum) and **`ActaClassification`** (record) in `shared.classify`. Add one-line javadoc on each constant that says what it means for import: PLAYED is written with its children, PENDING is stored as a scheduled fixture, PARTIAL is kept scheduled and reported, and INVALID is reported and not written. The compact constructor requires a non-null `completeness` and `reason`. It rejects `unresolvedPendingFixture == true` unless `completeness == PENDING`.

2. **Add `ActaCompletenessClassifier.hasResult(ActaGame)`.** It returns true when `sets()` is not empty, **or** `winner()` is non-blank, **or** `setsWon()` is non-null with at least one non-null side. `no_disputado` does not count as a result.

3. **Implement `classify(Acta, List<ActaGame>)`** with the ordered rules of analysis 4.1. The first matching rule wins:
   1. `published()` is `Boolean.FALSE` → `PENDING`, reason `"acta_publicada is false"`. Return at once. Do not inspect games, lineups, `abcIsHome`, or `finalResult` (G14).
   2. `published()` is `Boolean.TRUE` and any of these holds → `INVALID`. The reason names the first broken rule in this order:
      - `games` is empty
      - no game `hasResult`
      - `lineups()` is null, or either side is empty
      - `abcIsHome()` is null
   3. `published()` is `Boolean.TRUE` → `PLAYED`. Games with `wasNotPlayed()` count as complete (G20). No rule looks at games that are neither.
   4. `published()` is `null` (legacy) and no game `hasResult` (this covers empty actas and G17 "decided 0-0") → `PENDING`.
   5. `published()` is `null` and every game `hasResult` or `wasNotPlayed()` → `PLAYED`.
   6. Otherwise (`published()` is `null`, and some games have a result while others have neither a result nor `no_disputado`) → `PARTIAL`. The reason gives the count of incomplete games.

   For every `PENDING` result, set `unresolvedPendingFixture` when `acta.teams()` is null or either side's `name()` is null or blank (G18, R9). Team ids are not required, because legacy BCNESA pending fixtures have names but no ids.
   `classify(Acta)` delegates to `classify(acta, acta.games())`.

4. **Class javadoc.** Summarise the rule order, the definition of "has a result", and the invariant "`resultado_final` is never read". Point to `docs/acta-model-definition.json`. State that the lineup rule is "non-empty sides", not the schema's `minProperties: 3` (see Notes).

5. **Add `ActaCompletenessClassifierTest`** in `tt-data-league-import/src/test/java/org/cttelsamicsterrassa/data/load/shared/classify/` (JUnit 5).
   - **Rule tests** on synthetic actas built through the canonical `Acta` constructor. Use a private test helper `acta(Boolean published, Boolean abcIsHome, ActaLineups lineups, ActaTeams teams, List<ActaGame> games)` that passes `null` for every other component, plus small `game(...)`, `lineups(int home, int away)`, and `teams(String home, String away)` helpers. Add at least one test per step, plus:
     - Step 1 wins over content: an unpublished acta with played games, full lineups, and a winner-bearing `ActaFinalResult` → PENDING. This proves the order and that `resultado_final` is ignored.
     - `resultado_final` is not read: a published acta and a legacy acta whose `finalResult` claims a different winner, or is null, get the same class as with a consistent value.
     - Each INVALID sub-rule on its own: no games, all games `no_disputado`, an empty home side, an empty away side, a null `lineups`, and a null `abcIsHome`.
     - A published acta with `no_disputado` games mixed with played games → PLAYED (G20).
     - A published acta with 2 players per side → PLAYED.
     - Legacy: every game `no_disputado` → PENDING (G17). Played games plus `no_disputado` games → PLAYED. Played games plus games with neither → PARTIAL.
     - `hasResult`: sets only, winner only, `setsWon` only, `setsWon` with both sides null (false), blank winner (false), `no_disputado` with nothing else (false).
     - Unresolved: pending with null `teams`, a null home name, or a blank away name → unresolved. Pending with both names and no ids → resolved. PLAYED with null team names → `unresolvedPendingFixture` false.
     - `classify(acta, games)` uses the given games and not `acta.games()`.
     - `ActaClassification` rejects `unresolvedPendingFixture` on a non-PENDING class.
   - **Fixture tests.** Run a `@ParameterizedTest` with `@CsvSource(fixtureName, expectedCompleteness, expectedUnresolved)` over all 17 files in `src/test/resources/actas/`, with the expected values from the Starting-state table. Parse them with `ActaParser`, using the same `fixture(name)` resource lookup as `IncrementalActaFixturesTest`. Add named `@Test`s that make the acceptance cases explicit: FCTT 6-0, BCNESA 4-4, and RFETM decided 0-0 → PENDING, and the FCTT no-team placeholder → PENDING and unresolved.

6. **Documentation.** Update no README and no `rfetm-datamodel.md`: there is no CLI, configuration, or persistence change. Do not change `docs/acta-model-definition.json` validation rules here (see Notes).

7. **Validate.** Run `mvn -pl tt-data-league-import -am test`, then `mvn test` from the root. Compare the full-reactor result with the pre-existing failures recorded in FEAT-00075 (4 `InitialUserProvisioningServiceTest`, 8 import-processor tests). Report any other failure. Review the diff: only the three main classes and one test class should change. `grep` for `finalResult` in `shared/classify` must find nothing.

# Implementation Guidelines

- Affected modules: import.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- Pure import-module code with no domain, JPA, Spring, or runtime dependency. It depends only on the `shared.parse.acta` records.
- Out of scope: wiring the classifier into any processor, navigator, splitter, or preview. The FCTT `isPublished()` early returns stay. This is FEAT-00081 (T6), the BCNESA splitter change (G4), and T12 preview. Also out of scope: run counters (T7), `MatchStatus` (FEAT-00077), and schema validation-rule changes.
- Do not change `Acta.isPublished()`. Existing callers rely on "missing means published". The classifier reads `published()` to tell legacy (null) from explicit `true`.
- No broad catches and no silent defaults. Every acta gets exactly one class, and the `reason` explains it.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size S, Slice 1: lifecycle foundation. Depends on: FEAT-00075 (T0).
- 2026-09-27: Build plan written; status `idea` → `planned`. FEAT-00075 is done, so the dependency is satisfied.
- 2026-09-27: **Decision: the published-lineup rule is "both sides non-empty", not "at least 3 players per side".** Analysis 4.1 step 2 and the schema `else` branch (`minProperties: 3`) require 3 players. A scan of `C:\git\fctt-extract\resources\actas-json` shows that all 112 published FCTT 2025-2026 female actas have 2 players per side. BCNESA (16,387) and FCTT male (403) always have 3. The 3-player rule would classify every FCTT women's result as `INVALID` and drop it. The T0 fixture `acta_fctt_female_groupless.json` (2 / 2) guards this. A sixth acceptance criterion was added for it. This contradicts analysis section 2.5, which reported ≥ 3 players for all 515 FCTT files.
- 2026-09-27: Follow-up, not in this feature: `docs/acta-model-definition.json` `else` branch `minProperties: 3` rejects real FCTT female actas. Relax it (for example to 2, or make it per-source) in a separate schema change, and correct analysis section 2.5.
- 2026-09-27: Design choices: a separate `shared.classify` package, because the parse records "apply no interpretation". There is a `classify(acta, games)` overload so that T6 can classify each BCNESA fixture after the split. A `setsWon` score with both sides null, or a blank `ganador`, is not a result. Unresolved detection needs team **names**, not ids, because legacy BCNESA pending fixtures have names without ids. A published acta whose games are all `no_disputado` (a published walkover) is `INVALID` by rule 2 ("no game with a result"). No such file exists in the exports, and this follows the analysis.
- 2026-09-27: Implemented ActaCompleteness, ActaClassification, and ActaCompletenessClassifier in shared.classify with 46 JUnit 5 tests (rule tests + fixture sweep over all 17 actas). mvn -pl tt-data-league-import -am test: only the 4 pre-existing InitialUserProvisioningServiceTest failures (core-domain) and 8 pre-existing import-processor/consolidation failures (BcnesaImportProcessorsTest x3, FcttImportProcessorsTest x1, ImportProcessorsTest x3, TeamToClubConsolidationProcessorTest x1) remain, matching the FEAT-00075 baseline exactly. grep for finalResult/cumulativeScore in shared/classify finds only the javadoc reference. All six acceptance criteria verified and checked.
