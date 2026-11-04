# Build Plan
Source task: **T6** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.3; gaps G1, G2, G4, G15, G18; risks K13, K15, K16).

## Acceptance Criteria
- [x] RFETM, FCTT and BCNESA match processors implement the shared algorithm of analysis section 4.3 (create, upgrade, reschedule, skip, regression issue)
- [x] RFETM classifies actas before buildMatch and never stores unpublished actas as played
- [x] FCTT replaces the FEAT-00074 unpublished skip with the SCHEDULED branch and skips no-team placeholders before the team processor
- [x] BcnesaMatchdaySplitter yields one fixture named by equipos when partidos is empty
- [x] The SCHEDULED branch never copies resultado_final or a winner; a PLAYED match is never downgraded
- [x] The doubles path handles jugadores: [] without creating a DoublesPair
- [x] FCTT preview wording reflects the new behaviour
- [x] Tests cover create, upgrade, reschedule, idempotent re-import and regression for each source

## Baseline (verified 2026-09-28, after commit 2159cd4)

- Dependencies are delivered: FEAT-00076 `ActaCompletenessClassifier` / `ActaClassification` /
  `ActaCompleteness` (`tt-data-league-import/.../load/shared/classify/`), FEAT-00077 `MatchStatus`
  with the builder invariant "`SCHEDULED` ⇒ no winner, no games/sets won", FEAT-00079 read-side
  filtering, and FEAT-00080 `MatchRepository.replaceMatchContent(MatchContent)`,
  `updateSchedule(UUID, MatchSchedule)` and `Match.hasSameNaturalKeyAs` (JPA and in-memory).
- All three match processors (`RfetmMatchImportProcessor`, `FcttMatchImportProcessor`,
  `BcnesaMatchImportProcessor`, `ORDER = 30`) share one shape: resolve teams → `findMatchByNaturalKey`
  → **return early if present** (G1) → `buildMatch` with `UUID.randomUUID()` + `createNew()` →
  `saveMatch` → `saveLineups` → `storeGames` (builds and saves games, set scores, doubles pairs).
  BCNESA has no `SetScoreRepository` and derives games won from the fixture's games.
- RFETM never looks at `acta_publicada` (G2). FCTT returns early on `!isPublished()` (FEAT-00074),
  but only **after** `FcttActaOrientation.toHomeAway`, whose `isMirrored` reads `resultado_final`,
  which violates G14 for unpublished actas. `BcnesaMatchdaySplitter.split` returns `List.of()` for an
  acta with no games (G4).
- Navigators turn only processor exceptions into `ImportExecutionIssue`s, and any issue makes the
  run `FAILURE` (`NavigatorImportExecutionService.execute`). There is no channel for non-fatal
  "reported, not written" outcomes. `ImportRunContext` is per-run mutable state that every
  report context already carries.
- `ActaParticipant` already normalises `jugadores: null` to `List.of()`, and every doubles loop
  iterates it, so `jugadores: []` yields no `DoublesPair` today. This is covered only by tests,
  not by an explicit contract.
- Reference fixtures from FEAT-00075 live in `tt-data-league-import/src/test/resources/actas/`
  (`acta_rfetm_2026_published/unpublished`, `acta_rfetm_2025_decided_0_0`, `acta_rfetm_legacy_empty`,
  `acta_bcnesa_2026_unpublished`, `acta_bcnesa_2026_placeholder_4_4`, `acta_fctt_2026_published`,
  `acta_fctt_unpublished`, `acta_fctt_2026_no_team_placeholder`, `acta_fctt_2025_placeholder_6_0`,
  `acta_fctt_female_groupless`).
- The import module has 8 pre-existing processor-test failures (recorded in FEAT-00080 notes).
  This feature must not add to them. Any of them touched here should be fixed or explicitly
  re-recorded.

## Contracts (import module, package `org.cttelsamicsterrassa.data.load.shared.match.lifecycle`)

1. `enum MatchLifecycleOutcome { SCHEDULED_CREATED, PLAYED_CREATED, UPGRADED_TO_PLAYED, RESCHEDULED,
   UNCHANGED, PLAYED_KEPT, REGRESSION_REPORTED, PARTIAL_REPORTED, INVALID_REPORTED }`.
   `isReportable()` is true for the three `*_REPORTED` values.
2. `interface MatchLifecycleSource`: the per-source callbacks the shared algorithm needs, implemented
   by each processor:
   - `Match buildScheduledMatch(UUID id)`: header only (competition, season, group, round, phase,
     date/time, city, venue, teams, head referee name); status `SCHEDULED`; **never** reads
     `finalResult()`, sets no winner, games or sets won. Built with `createNew()` for a new id.
   - `MatchContent buildPlayedContent(UUID id, boolean existing)`: today's `buildMatch` /
     `buildLineups` / game-building code refactored to build (not save) the header, lineups,
     games, set scores and doubles pairs for the given id, using `createExisting()` when `existing`.
3. `final class MatchLifecycleWriter` (plain class, constructed by each processor with its
   `MatchRepository` and the child repositories; not a Spring bean). One method:
   `MatchLifecycleOutcome apply(ActaClassification classification, Optional<Match> existing, MatchLifecycleSource source)`
   implementing analysis 4.3:

   | existing | PLAYED | PENDING | PARTIAL | INVALID |
   |---|---|---|---|---|
   | none | save `buildPlayedContent(random, false)` via `saveMatch` + child `save*` → `PLAYED_CREATED` | `saveMatch(buildScheduledMatch(random))` → `SCHEDULED_CREATED` | same as PENDING, then `PARTIAL_REPORTED` | same as PENDING, then `INVALID_REPORTED` |
   | `SCHEDULED` | `replaceMatchContent(buildPlayedContent(existing.id, true))` → `UPGRADED_TO_PLAYED` | reschedule rule → `RESCHEDULED` / `UNCHANGED` | reschedule rule, then `PARTIAL_REPORTED` | reschedule rule, then `INVALID_REPORTED` |
   | `PLAYED` | nothing → `PLAYED_KEPT` (correction is FEAT-00089) | nothing → `REGRESSION_REPORTED` | nothing → `REGRESSION_REPORTED` | nothing → `INVALID_REPORTED` |

   - **Reschedule rule:** build the incoming `MatchSchedule` from `buildScheduledMatch`. A `null`
     incoming component keeps the stored value, which resolves the FEAT-00080 open question.
     Call `updateSchedule` only when the merged schedule differs from the stored one. This makes
     re-imports idempotent (R6).
   - The `PLAYED_CREATED` path keeps today's write sequence (`saveMatch`, then `saveLineups`,
     `saveGames`, `saveSetScores`, `saveDoublesPairs`) so behaviour for first-time played actas is
     unchanged; only the building is shared with the upgrade path.
4. `ImportRunContext` gains `recordMatchOutcome(MatchLifecycleOutcome, Path location, String reason)`,
   `matchOutcomeCounts()` (an unmodifiable `EnumMap` copy) and `reportedMatchIssues()` (an
   unmodifiable list of `ImportExecutionIssue` for reportable outcomes, with the processor name,
   the file and the reason). Processors log reportable outcomes at WARN. **This feature does not feed
   them into `TraversalSummary`, metrics or run status**; that is FEAT-00082 (see Notes).

## Implementation order

1. **Lifecycle core.** Add `MatchLifecycleOutcome`, `MatchLifecycleSource` and `MatchLifecycleWriter`
   under `shared/match/lifecycle/`, and the `ImportRunContext` additions. Unit-test
   `MatchLifecycleWriterTest` against `InMemoryRepositories` (wired `Matches` constructor from
   FEAT-00080), covering every cell of the table, the null-keeps-stored reschedule merge,
   no-op on an equal schedule, and that `buildPlayedContent` is never invoked for PENDING, PARTIAL
   or INVALID.
2. **RFETM** (`rfetm/process/RfetmMatchImportProcessor.java`):
   - Classify with `ActaCompletenessClassifier.classify(acta)` before any build (AC 2). Unresolved
     pending fixtures are already skipped by the navigator when a side has no club key; keep the
     processor-side `resolveTeam` guard.
   - Replace the skip-if-exists block with `writer.apply(...)`. Split `buildMatch` into
     `buildScheduledMatch` / the header part of `buildPlayedContent` (id parameter, no `finalResult()`
     read in the scheduled path), and split `storeGames` into a pure builder returning games, set
     scores and pairs.
   - Record the outcome on `context.runContext()`.
3. **FCTT** (`fctt/process/FcttMatchImportProcessor.java`, `fctt/traverse/FcttActasDirectoryNavigator.java`):
   - Processor: classify the **raw** acta first. Apply `FcttActaOrientation.toHomeAway` only when the
     class is `PLAYED`, so an unpublished acta's `resultado_final` is never read (G14). Remove the
     FEAT-00074 `!isPublished()` early return in the same change (AC 3, risk K15).
   - Navigator: before `dispatch`, classify; when `unresolvedPendingFixture` (G18 no-team placeholder),
     count it as skipped, log WARN and do not dispatch to any processor, so the team processor never
     sees it (AC 3). File names are never used for identity (G15): the natural key already comes from
     the payload.
   - Preview (`FcttPreviewValidationProcessor`): for an unpublished acta, info
     `"FCTT acta not published; fixture will be stored as a scheduled match without result."`; for a
     no-team placeholder, info `"FCTT pending fixture has no teams; it will be reported as unresolved and not stored."`
     instead of the current "incomplete teams" / "team has no name" errors. Keep those errors for
     published actas. Update `FcttPreviewValidationProcessorTest` (AC 7).
4. **BCNESA** (`bcnesa/traverse/BcnesaMatchdaySplitter.java`, `bcnesa/process/BcnesaMatchImportProcessor.java`):
   - Splitter: when `acta.games()` is empty, return exactly one `Fixture(equipos home, equipos away, List.of())`.
     Null names stay null, so the navigator's existing `fixturesUnresolved` path handles them (AC 4).
     Update the class javadoc ("A report with no games yields no fixtures").
   - Processor: classify per fixture with `classify(context.acta(), context.games())`. The
     scheduled header must not call `context.homeGamesWon()` / `awayGamesWon()` or
     `resolveWinnerTeam`. Set scores stay an empty list (BCNESA stores none).
   - Check that `BcnesaTeamImportProcessor` registers both teams for a no-games fixture, and that
     `BcnesaPlayerImportProcessor` is a no-op for it.
5. **Doubles `jugadores: []`** (AC 6): make no production change unless a test fails. Add one test
   per source with a PLAYED acta whose doubles game has `jugadores: []` on one side. Assert that the
   game is stored, that no `DoublesPair` is created for that side, and that nothing is thrown.
6. **Processor tests** (in `tt-data-league-import/src/test/java/.../load/process/`: `ImportProcessorsTest`
   (RFETM), `FcttImportProcessorsTest`, `BcnesaImportProcessorsTest`), using the FEAT-00075 fixtures
   and wired in-memory repositories. For each source (AC 8):
   - **create:** unpublished acta → one `SCHEDULED` match, no children, null results and winner
     (for FCTT use `acta_fctt_2025_placeholder_6_0`, and for BCNESA `acta_bcnesa_2026_placeholder_4_4`,
     to prove that the fake result is ignored; AC 5).
   - **upgrade:** unpublished then published acta of the same fixture → same UUID, `PLAYED`, children
     present, no duplicate match.
   - **reschedule:** unpublished acta, then the same acta with a changed date/venue → fields updated,
     still `SCHEDULED`. Then the same acta with a null date → stored date kept.
   - **idempotent re-import:** processing the same snapshot twice produces `UNCHANGED` / `PLAYED_KEPT`
     and identical repository contents.
   - **regression:** published then unpublished acta → match stays `PLAYED` with its children;
     `REGRESSION_REPORTED` recorded on the run context.
   - RFETM extras: `acta_rfetm_2025_decided_0_0` and `acta_rfetm_legacy_empty` → `SCHEDULED` (G17).
     A legacy partial acta → `SCHEDULED` + `PARTIAL_REPORTED`.
   - FCTT extras: `acta_fctt_2026_no_team_placeholder` through the navigator → no team, club or match
     created; counted as skipped.
   - BCNESA extras: splitter unit test for the empty-`partidos` fixture; navigator test that
     `acta_bcnesa_2026_unpublished` now dispatches one fixture.
   - Published but INVALID acta → one `SCHEDULED` match with no children, no results and no winner,
     plus `INVALID_REPORTED`. A second INVALID import of the same fixture leaves it unchanged, and a
     stored `PLAYED` match is never touched.
7. **Documentation.** Update `tt-data-league-import/README.md` (or the module README section that
   describes processor behaviour) to describe the lifecycle: pending actas become `SCHEDULED`, an
   upgrade keeps the id, a played match is never downgraded, and FCTT/BCNESA pending handling. Add a
   note to `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` only if the wording there
   about import behaviour becomes stale. No schema change.
8. **Validation.** `mvn -pl tt-data-league-import -am test`, then the full `mvn test`. Compare with the
   recorded baseline of pre-existing failures, and review the diff.

# Implementation Guidelines

- Affected modules: import.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- Keep source-specific parsing, team resolution, round/group/phase rules and winner resolution in each
  processor; only the create/upgrade/reschedule/skip decision is shared.
- Never read `Acta.finalResult()` (or run FCTT orientation, which reads it) for an acta that is not
  classified `PLAYED` (G14). Classification never uses `resultado_final`.
- A `PLAYED` match is never downgraded or rescheduled by import. Corrections of played matches are
  FEAT-00089.
- Upgrade goes through the single transactional `replaceMatchContent`. Do not emulate it with
  `saveMatch` plus child saves.
- Reportable outcomes are not exceptions: do not throw for them, and do not add broad catches. Genuine
  failures (repository or constraint errors) still propagate to the navigator's existing per-processor
  failure handling.
- Do not create teams, clubs or players for unresolved pending fixtures (R9). Do not use file names
  for identity (G15).
- Out of scope: run-status and counter exposure (FEAT-00082), `id_partido` persistence and cross-checks
  (FEAT-00083/85), jornada progress (FEAT-00084), and preview classification for RFETM/BCNESA
  (FEAT-00088).

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size M, Slice 2: incremental import. Depends on: FEAT-00076 (T1), FEAT-00080 (T5).
- FEAT-00079 (T4) must ship with or before this feature (risk K1).
- 2026-09-27: Added FEAT-00079 (T4) as a formal dependency to enforce that ordering.
- Until this feature ships, do not import any 2026-2027 export (risk K15).
- 2026-09-28: Build plan written; status `planned`. Dependencies FEAT-00076/77/79/80 are done
  (commit 2159cd4 and earlier).
  - Decision: one shared `MatchLifecycleWriter` plus a per-processor `MatchLifecycleSource`, instead of
    three copies of the decision table. The PLAYED-create path keeps today's write sequence.
  - Decision (resolves the FEAT-00080 open question): when rescheduling, a null incoming schedule
    field keeps the stored value, and `updateSchedule` runs only when the merged schedule differs.
  - Decision: FCTT classifies the raw acta and orients it only when it is PLAYED, because
    `FcttActaOrientation.isMirrored` reads `resultado_final`.
  - Decision: reportable outcomes (INVALID, PARTIAL, regression) are recorded on `ImportRunContext`
    and logged at WARN, but not yet added to traversal issues. Today any issue turns the run into
    `FAILURE` and skips consolidation, so a single legacy PARTIAL acta would fail a whole season.
    FEAT-00082 decides how counters and issues affect status. **Ship FEAT-00081 and FEAT-00082
    together** so operators see these outcomes somewhere other than the logs.
  - 2026-09-28 (user direction): every acta that is not played yet is imported as a `SCHEDULED`
    match. Beyond PENDING and PARTIAL, this now also covers INVALID (published but no games, results
    or lineups): it creates or reschedules a `SCHEDULED` match with no children, and is still
    reported. This deviates from analysis 4.1 step 2 ("write nothing"). The only acta not stored is
    a no-team placeholder (G18), because it has no natural key; it is reported as unresolved.
  - Open question: should the player import processors skip actas that are not `PLAYED`?
    Unpublished actas seen so far carry empty lineups, so it is harmless today; revisit if an
    unpublished acta with a placeholder lineup appears.
- 2026-09-28: Implemented; status `in-review`.
  - Delivered as planned: `shared/match/lifecycle/` (`MatchLifecycleOutcome`, `MatchLifecycleSource`,
    `MatchLifecycleWriter` — the second constructor without `SetScoreRepository` covers BCNESA), the
    `ImportRunContext` outcome counters and reported issues, the three processors, the FCTT navigator
    placeholder skip, the FCTT preview wording, and the BCNESA splitter empty-`partidos` branch.
  - `recordMatchOutcome` gained a processor parameter so `reportedMatchIssues()` can carry the
    processor name in `ImportExecutionIssue` without a second lookup.
  - Tests: `MatchLifecycleWriterTest` (every table cell, null-keeps-stored merge, equal-schedule
    no-op, `buildPlayedContent` never called for PENDING/PARTIAL/INVALID) and
    `MatchLifecycleImportProcessorsTest` (create/upgrade/reschedule/idempotent/regression plus the
    placeholder, decided 0-0, legacy-empty, PARTIAL, INVALID and `jugadores: []` doubles cases for
    each source); `BcnesaMatchdaySplitterTest`, `BcnesaActasDirectoryNavigatorTest`,
    `FcttActasDirectoryNavigatorTest` and `FcttPreviewValidationProcessorTest` extended.
  - Behaviour change recorded in `tt-data-league-import-runtime/README.md`. No schema change, so
    `rfetm-datamodel.md` was untouched; `tt-data-league-import` has no README.
  - `ScheduledMatchBackfillServiceTest` seeds its legacy-empty/decided-0-0 fixtures through the real
    processors; since FEAT-00081 imports those shapes directly as `SCHEDULED`, the test restores the
    pre-feature `PLAYED` placeholder state before exercising the backfill (the FEAT-00078 legacy
    repair path is unchanged).
  - Validation: full `mvn test` — only the pre-existing failures remain (4
    `InitialUserProvisioningServiceTest` failures in core-domain and the 8 recorded processor-test
    failures in the import module: `BcnesaImportProcessorsTest` ×3, `FcttImportProcessorsTest` ×1,
    `ImportProcessorsTest` ×3, `TeamToClubConsolidationProcessorTest` ×1). The 2026-09-28 baseline
    was re-recorded before this change and is identical.
  - K15 lifted: 2026-2027 exports can now be imported; outcomes are visible in logs and the run
    context. FEAT-00082 must still land before operators see counters in the run summary.
- 2026-09-28: Closed `done` after explicit user approval; review found no follow-up changes.
