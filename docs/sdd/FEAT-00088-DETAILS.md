# Build Plan
Source task: **T12** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.8; gap G11; risks K2, K14).

## Acceptance Criteria
- [x] Preview reports counts of published, unpublished, invalid, partial and unresolved actas for RFETM, BCNESA and FCTT
- [x] Preview reports new scheduled matches, upgrades, reschedules and regressions per competition/group
- [x] Preview reports the resulting jornada progress
- [x] Preview flags duplicate id_partido within a snapshot

## Baseline (verified 2026-09-29, after FEAT-00087 / commit 0951971)

- Preview is synchronous: `StartImportPreviewCommandHandler` → `ImportResourcePreviewService.preview(ImportResource)`
  → `ImportPreviewResultDtoMapper.toDto` → `POST .../import/preview` (`ImportResourceController`). Nothing
  is persisted; `ImportPreviewResult` is a domain record (`status`, `validationFindings`,
  `processingErrors`, `filesSeen`, `itemsDispatched`, `skipped`, `processorFailures`).
- `NavigatorBackedImportResourcePreviewService` (import module, `@Primary` `@Component`) only holds the three
  navigators and runs one validation processor per source (`RfetmPreviewValidationProcessor`,
  `BcnesaPreviewValidationProcessor`, `FcttPreviewValidationProcessor`) that write text findings into
  `ImportPreviewCollector`. Only FCTT classifies (via `ActaCompletenessClassifier`), and only as text (gap G11).
- The create/upgrade/reschedule/skip decision lives in `MatchLifecycleWriter.apply`, interleaved with the
  writes. PARTIAL/INVALID actas on a stored SCHEDULED match may also run the reschedule rule while the
  returned outcome stays `PARTIAL_REPORTED`/`INVALID_REPORTED`, so outcome alone cannot count reschedules.
- The three match processors (`RfetmMatchImportProcessor`, `FcttMatchImportProcessor`,
  `BcnesaMatchImportProcessor`) own the per-source natural key (competition / group / round / phase; RFETM
  `groupNumber = acta.group() ?: 0`, `phase = null`), the fixture id rule (BCNESA `sourceFixtureId(context,
  acta)`, index 0 only; RFETM/FCTT `acta.matchId()`), the team lookup
  (`TeamRepository.findTeamByNameAndSeasonAndSource`, read-only) and the `MatchLifecycleSource` header
  builders (private inner classes). They also write to `ImportRunContext` (`recordSnapshotFixture`,
  `recordMatchOutcome`, RFETM `recordRoundFallback`).
- Navigators accept an optional `ImportRunContext`; FCTT records navigator-skipped unresolved pending
  fixtures there (`recordUnresolvedPendingFixture`), BCNESA reports unattributed fixtures as
  `BcnesaTraversalSummary.fixturesUnresolved()`.
- Jornada progress (FEAT-00084): `MatchRepository.findRoundProgress(source, season)` =
  `RoundProgressCalculator.compute(source, season, List<RoundStatusCount>)`; JPA feeds it from
  `MatchRepositoryHelper.countByRoundAndStatus`, the in-memory repo from its saved list. `RoundProgressDto` /
  `RoundProgressDtoMapper` already exist in `application/importresource/shared/dto`.
- `MatchRepository` implementors: `MatchRepositoryJpa`, `InMemoryRepositories.Matches` and a test stub in
  `SnapshotReconcilerTest`.

## Contracts

1. **Lifecycle planner** (`shared/match/lifecycle`, import module). Behavior-preserving extraction of the
   decision out of `MatchLifecycleWriter`:
   - `enum MatchLifecycleAction { CREATE_PLAYED, CREATE_SCHEDULED, UPGRADE_TO_PLAYED, UPDATE_SCHEDULE, NONE }`.
   - `record MatchLifecyclePlan(MatchLifecycleOutcome outcome, MatchLifecycleAction action,
     MatchSchedule mergedSchedule)`; `mergedSchedule` non-null iff `action == UPDATE_SCHEDULE`.
   - `final class MatchLifecyclePlanner` (stateless, no repositories):
     `MatchLifecyclePlan plan(ActaClassification, Optional<Match> existing, MatchLifecycleSource source)`
     implementing exactly the current decision table and reschedule merge rule. It calls
     `source.buildScheduledMatch(stored.getId())` only when a stored SCHEDULED match exists, and never
     `buildPlayedContent`. `static MatchLifecyclePlan planCreation(ActaClassification)` gives the plan for
     an absent match without a source (PLAYED → `CREATE_PLAYED`/`PLAYED_CREATED`; PENDING →
     `CREATE_SCHEDULED`/`SCHEDULED_CREATED`; PARTIAL/INVALID → `CREATE_SCHEDULED` with the reported outcome).
   - `MatchLifecycleWriter.apply` becomes `plan` + execute-the-action; its public signature, outcomes and
     writes are unchanged (`MatchLifecycleWriterTest` and `MatchLifecycleImportProcessorsTest` pass untouched).
2. **Per-fixture preview** (`shared/preview`, import module):
   - `enum PreviewChange { NEW_SCHEDULED, NEW_PLAYED, UPGRADE, RESCHEDULE, UNCHANGED, PLAYED_KEPT,
     REGRESSION, INVALID_ON_PLAYED, IDENTITY_CONFLICT, NOT_STORED }`, derived from a plan by
     `PreviewChange.of(MatchLifecyclePlan)`: `CREATE_SCHEDULED` → NEW_SCHEDULED, `CREATE_PLAYED` → NEW_PLAYED,
     `UPGRADE_TO_PLAYED` → UPGRADE, `UPDATE_SCHEDULE` → RESCHEDULE (whatever the reported outcome),
     `NONE` + `REGRESSION_REPORTED` → REGRESSION, `NONE` + `INVALID_REPORTED` → INVALID_ON_PLAYED,
     `NONE` + `PLAYED_KEPT` → PLAYED_KEPT, other `NONE` → UNCHANGED.
   - `record FixturePreview(ImportSource source, String competition, Integer groupNumber, String phase,
     int round, String sourceFixtureId, String homeTeamName, String awayTeamName,
     ActaClassification classification, PreviewChange change, Integer existingRound,
     boolean teamsPendingRegistration, String reason, Path location)`. `existingRound` is the stored
     match's round for UPGRADE (so projection moves the right row); `teamsPendingRegistration` is true when
     a team is not registered for the season yet, in which case the change is `planCreation(...)`'s (the
     real run registers teams in the team processor before the match processor).
3. **Match processor preview entry point**, one per source:
   `public FixturePreview preview(<Source>MatchReportContext context)` on each match processor. `process`
   and `preview` share one private resolution step (classification, orientation, competition/group/round/
   phase, fixture id, both team lookups, `findMatchByNaturalKey`, `MatchFixtureIdentityGuard.conflict`), so
   the key and fixture-id rules cannot diverge. `preview` then calls `MatchLifecyclePlanner.plan` (or
   `planCreation` when teams are unregistered) instead of the writer, and **never** calls a repository
   write, `recordSnapshotFixture` or `recordMatchOutcome`. Early exits that `process` logs and skips
   (no payload, incomplete teams, invalid FCTT group folder) return `NOT_STORED` with the same reason;
   unresolved pending fixtures return `NOT_STORED` with `classification.unresolvedPendingFixture()` true.
   A guard conflict returns `IDENTITY_CONFLICT` with the guard's reason.
4. **Preview adapters**: `RfetmPreviewClassificationProcessor implements MatchContextProcessor`,
   `FcttPreviewClassificationProcessor implements FcttMatchReportProcessor`,
   `BcnesaPreviewClassificationProcessor implements BcnesaMatchReportProcessor` (each in its source's
   `process` package), constructed with the match processor bean and an `IncrementalPreviewCollector`;
   `process(context)` = `collector.add(matchProcessor.preview(context))`. They run after the existing
   validation processors in the same traversal; validation processors are unchanged.
5. **`IncrementalPreviewCollector`** (`shared/preview`), per preview run:
   - Acta buckets from `FixturePreview.classification`: `published` = PLAYED, `unpublished` = PENDING and
     not unresolved, `partial` = PARTIAL, `invalid` = INVALID, `unresolved` = unresolved pending fixtures
     plus navigator-level unresolved items (FCTT `ImportRunContext` unresolved-pending records, BCNESA
     `fixturesUnresolved`) - each item counted once (step 1 verifies which path each source takes).
     Legacy actas without `acta_publicada` are bucketed by their classification.
   - Change counts per `(competition, groupNumber, phase)` for every `PreviewChange`, plus
     `teamsPendingRegistration` count.
   - Duplicate `id_partido`: non-null `sourceFixtureId` → locations; any id seen more than once in the run
     is a duplicate (risk K14).
   - `projection deltas`: NEW_SCHEDULED → +1 SCHEDULED at (scope, round); NEW_PLAYED → +1 PLAYED;
     UPGRADE → -1 SCHEDULED at (scope, existingRound), +1 PLAYED at (scope, round). A fixture repeated in
     the snapshot (same fixture id, or same scope/round/home/away names) contributes its delta once, the
     last dispatched one winning, matching the write path where the second write sees the first.
6. **Domain port** `MatchRepository.findRoundStatusCounts(ImportSource source, Season season)` →
   `List<RoundStatusCount>`: abstract, both arguments required (`NullPointerException`), source-scoped,
   read-only. JPA: the existing `countByRoundAndStatus` mapping moved out of `findRoundProgress`, which
   becomes `RoundProgressCalculator.compute(source, season, findRoundStatusCounts(source, season))`;
   in-memory likewise. No schema or index change.
7. **Domain result** (`tt-data-league-core-domain/.../domain/load/model`):
   - `record PreviewActaCounts(long published, long unpublished, long partial, long invalid, long unresolved)`
     (non-negative).
   - `record PreviewScopeChanges(String competition, Integer groupNumber, String phase, long newScheduled,
     long newPlayed, long upgrades, long reschedules, long unchanged, long playedKept, long regressions,
     long invalidOnPlayed, long identityConflicts, long notStored)`.
   - `record PreviewDuplicateFixtureId(String sourceFixtureId, List<String> locations)` (>= 2 locations).
   - `record ImportPreviewClassification(PreviewActaCounts actas, List<PreviewScopeChanges> changes,
     long teamsPendingRegistration, List<RoundProgress> currentProgress, List<RoundProgress> projectedProgress,
     List<PreviewDuplicateFixtureId> duplicateFixtureIds)` with `static empty()`; lists copied, sorted like
     `RoundProgressCalculator` output (competition, group, phase; nulls last).
   - `ImportPreviewResult` gains a trailing `ImportPreviewClassification classification` component
     (`null` → `empty()`); existing `success`/`empty`/`failure` factories keep their signatures and pass
     `empty()`, plus overloads that take a classification.
8. **DTO** (`application/importresource/preview/dto`): `ImportPreviewClassificationDto`,
   `PreviewActaCountsDto`, `PreviewScopeChangesDto`, `PreviewDuplicateFixtureIdDto`; progress reuses
   `RoundProgressDto` via `RoundProgressDtoMapper`. `ImportPreviewResultDto` gains a trailing
   `classification` field; `ImportPreviewResultDtoMapper.toDto` and `missingResource` (empty classification)
   fill it.
9. **Preview service**: `NavigatorBackedImportResourcePreviewService` constructor adds the three match
   processor beans and `MatchRepository`. Per source it creates one `ImportRunContext(source, season)`, one
   `ImportPreviewCollector` and one `IncrementalPreviewCollector`, traverses with
   `[validation processor, classification processor]` and that run context, then:
   `current = matchRepository.findRoundProgress(source, season)`;
   `projected = RoundProgressCalculator.compute(source, season, stored findRoundStatusCounts + deltas)`
   (rows that drop to 0 are removed; a negative count is an `IllegalStateException`, not a silent clamp);
   builds `ImportPreviewClassification`; and adds human-readable `info` findings (one acta-bucket line,
   one line per scope with any non-UNCHANGED/PLAYED_KEPT change, one line per projected progress row) and
   one `warning` per duplicate `id_partido` and per identity conflict. The classification never changes the
   preview status (still decided by `ImportPreviewCollector.toResult`); `toResult` gets an overload that
   takes the classification.

## Steps

1. **Verify baseline paths.** Confirm for each navigator where unresolved pending fixtures go (dispatched to
   processors vs. `recordUnresolvedPendingFixture`/`fixturesUnresolved`) so contract 5 counts each once;
   confirm the team processors register teams of unpublished actas (so `teamsPendingRegistration` →
   creation is right). Record findings in `# Notes`; adjust contract 5 if a path differs.
2. **Planner extraction** (contract 1). Add `MatchLifecycleAction`, `MatchLifecyclePlan`,
   `MatchLifecyclePlanner`; rewrite `MatchLifecycleWriter.apply` on top of it. Add
   `MatchLifecyclePlannerTest` covering every cell of the decision table (none/SCHEDULED/PLAYED ×
   PLAYED/PENDING/PARTIAL/INVALID), the unchanged-schedule case, PARTIAL/INVALID with a changed schedule
   (`UPDATE_SCHEDULE` + reported outcome), `planCreation`, and that the source is not invoked when
   `existing` is empty. Existing writer/lifecycle tests must pass unchanged.
3. **Round status port** (contract 6). Add `findRoundStatusCounts` to `MatchRepository`, `MatchRepositoryJpa`
   (+ `findRoundProgress` delegating), `InMemoryRepositories.Matches`, and the `SnapshotReconcilerTest` stub.
   Extend the JPA progress test with a `findRoundStatusCounts` assertion (source/season scoping, grouping by
   round and status).
4. **Domain result and DTO** (contracts 7-8). Add the records, extend `ImportPreviewResult`,
   `ImportPreviewResultDto` and the mapper; update constructor call sites (3 outside the model:
   domain tests, `ImportResourceControllerTest`). Add record-validation tests and a mapper test for a
   populated classification and for `missingResource`.
5. **Processor preview entry points** (contracts 2-3). Add `PreviewChange`, `FixturePreview`; refactor each
   match processor's `process` into shared resolution + write, and add `preview`. RFETM `resolveRound`'s
   fallback still records on the (preview) run context - acceptable, it is the preview's own context.
6. **Collector and adapters** (contracts 4-5). Add `IncrementalPreviewCollector` and the three
   `*PreviewClassificationProcessor` classes.
7. **Service wiring** (contract 9). Extend `NavigatorBackedImportResourcePreviewService`; confirm the api
   runtime and import runtime contexts still start (existing Spring context tests) since the service now
   needs `MatchRepository` and the match processor beans.
8. **Tests** (import module, in-memory repositories), `IncrementalPreviewTest` or extensions of
   `PreviewValidationProcessorTest`:
   - FCTT 2026-2027 `tercera-nacional/G1` shape (FEAT-00084 fixture): empty store → 3 published,
     9 unpublished, 3 NEW_PLAYED + 9 NEW_SCHEDULED, projected current round 1, no last complete round;
     after importing that snapshot, a second snapshot where 2 round-1 actas are now published and one
     round-2 acta has a new date → 2 UPGRADE, 1 RESCHEDULE, projected played 5.
   - Regression: stored PLAYED + incoming unpublished → REGRESSION, projection unchanged.
   - RFETM and BCNESA: at least one published, one unpublished and one partial (legacy) acta each, with
     per-scope counts (RFETM group 0 / phase null; BCNESA phase set, index-0 fixture id only).
   - Unresolved: FCTT pending fixture without teams and a BCNESA unattributed fixture each counted once.
   - Duplicate `id_partido`: two FCTT files with the same `id_partido` (unpublished + published copy)
     → one `PreviewDuplicateFixtureId` with both locations, a warning finding, projection counts the
     fixture once, status unchanged.
   - Unregistered teams → creation change and `teamsPendingRegistration` counted.
   - Identity conflict (FEAT-00085 drift case) → IDENTITY_CONFLICT and a warning.
   - **Read-only**: a `MatchRepository` decorator whose `saveMatch`/`saveMatches`/`replaceMatchContent`/
     `updateSchedule` throw, plus unchanged in-memory team/lineup/game counts after preview, and an
     `ImportRunContext` whose snapshot ledger and outcome counters stay empty.
9. **Documentation.** `tt-data-league-import-runtime/README.md` (administrator API preview section, around
   "The administrator API exposes the same preview/validate/...") and the api-runtime README if it
   documents the preview response: describe the `classification` block, bucket definitions, change kinds,
   projected progress and duplicate-id warnings. `rfetm-datamodel.md` unchanged (no schema change) - state
   this in `# Notes`.
10. **Validate.** `mvn -pl tt-data-league-import -am test`, then `mvn test` from the root; review the diff.

# Implementation Guidelines

- Affected modules: import (lifecycle planner, match processors, preview collectors/adapters, preview
  service), core-domain (`MatchRepository.findRoundStatusCounts`, preview result records and DTOs),
  core-repository-jpa (`findRoundStatusCounts`), api-rest (controller test only). No runtime wiring code
  changes are expected beyond Spring picking up the new constructor arguments.
- Preview is strictly read-only: no repository write, no `recordSnapshotFixture`/`recordMatchOutcome`, no
  persisted preview state. A read-only test pins this.
- One decision table: the preview must use `MatchLifecyclePlanner`, never a copy of the writer's rules, and
  the match processors' shared resolution step, never a copy of the natural-key/fixture-id rules.
- Keep lookups source-scoped (`findTeamByNameAndSeasonAndSource`, `findBySourceFixtureId(source, ...)`);
  never add external ids to `FederatedClub` or `FederatedPlayer`.
- The classification is informational: it never changes the preview status, never skips files, and never
  feeds the real import run.
- Out of scope: blocking an upload on preview results (FEAT-00087/FEAT-00093 own the upload contract),
  delta mode (FEAT-00090), amended-acta detection (FEAT-00089), a season calendar / total-round inference
  (FEAT-00092), duplicate natural keys other than `id_partido`, and frontend rendering of the new block.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 3: visibility and safety. Depends on: FEAT-00076 (T1), FEAT-00084 (T8).
- 2026-09-29: Build plan written against commit 0951971 (FEAT-00087). Status `idea` → `planned`. Decisions:
  (1) the lifecycle decision is extracted into a pure `MatchLifecyclePlanner` shared by the writer and the
  preview, and reschedules are counted by planned action, not outcome, because PARTIAL/INVALID actas can
  reschedule while reporting; (2) the preview reuses the match processors through a new `preview` entry
  point sharing their resolution step instead of re-deriving keys; (3) "published"/"unpublished" buckets
  follow the classifier (PLAYED/PENDING) so legacy actas without `acta_publicada` are covered; (4) projected
  progress = stored `RoundStatusCount`s (new abstract port `findRoundStatusCounts`, extracted from the
  existing JPA query) plus preview deltas, fed to the existing `RoundProgressCalculator`; current progress
  reuses `findRoundProgress` as FEAT-00084 anticipated; (5) duplicate `id_partido` is a warning, not an
  error, and the duplicated fixture counts once in the projection.
- 2026-09-29: Effort raised from small to medium: the planner extraction, three processor entry points,
  a new port method and the result/DTO extension span four modules.
- 2026-09-29: Open questions: whether the api-runtime README documents the preview response (step 9);
  whether the frontend preview view should render the new block (follow-up feature if yes). No schema
  change is planned, so `rfetm-datamodel.md` stays unchanged.
- 2026-09-29: Plan approved by the user; status `planned` → `ready`.
- 2026-09-29 (step 1, baseline verification): (a) Unresolved pending fixtures are skipped at navigator
  level in ALL THREE sources and never dispatched: RFETM (`RfetmActasDirectoryNavigator` ~L239-249) and
  FCTT (~L243-251) record them via `ImportRunContext.recordUnresolvedPendingFixture`; BCNESA
  (`dispatchFixture` ~L345-350) increments `counters.fixturesUnresolved` AND records on the same run
  context. Contract 5 therefore sources the `unresolved` bucket from the preview run's
  `ImportRunContext` records (one per item for every source); a defensively dispatched
  `FixturePreview` with `unresolvedPendingFixture` would also count, but that path is not reachable
  through the navigators. (b) Team processors (`@Order(10)`, all sources) register teams of unpublished
  actas with no published check, and run before the match processors (`@Order(30)`), so
  `teamsPendingRegistration` → `planCreation` is right. (c) The current preview service uses the 3-arg
  `traverseSeason` overload that allocates and discards an internal `ImportRunContext`; contract 9 must
  pass an explicit run context per source so unresolved records are visible to the collector.
  (d) RFETM resolves team keys in the navigator (`RfetmClubKey`), so its processor-level
  "incomplete teams" exit only triggers on lookup failure, matching the preview's
  `teamsPendingRegistration` semantics.
- 2026-09-29: Implemented (steps 2-10). Extracted `MatchLifecyclePlanner` (+ `MatchLifecycleAction`,
  `MatchLifecyclePlan`) from `MatchLifecycleWriter` (behaviour preserved; existing writer/lifecycle
  tests pass untouched). Added the abstract `MatchRepository.findRoundStatusCounts` port (JPA extracted
  the `countByRoundAndStatus` mapping out of `findRoundProgress`, which now delegates; in-memory and the
  two test stubs mirror it). Added domain `PreviewActaCounts`/`PreviewScopeChanges`/
  `PreviewDuplicateFixtureId`/`ImportPreviewClassification`, extended `ImportPreviewResult` (trailing
  `classification`, factory overloads) and the preview DTOs/mapper. Added `preview(...)` entry points on
  the three match processors sharing one private `resolve(...)` step, plus `PreviewChange`/`FixturePreview`,
  `IncrementalPreviewCollector` and the three `*PreviewClassificationProcessor` adapters, and wired them
  into `NavigatorBackedImportResourcePreviewService` (now also depends on the three match processors and
  `MatchRepository`, and passes an explicit `ImportRunContext` per source so navigator-skipped unresolved
  fixtures are counted once). Deviation from contract 5: the `unresolved` bucket is sourced solely from
  the preview run's `ImportRunContext` records (all three navigators skip-and-record unresolved fixtures
  and never dispatch them), so dispatched `FixturePreview`s never contribute to it - no double count.
  FCTT/BCNESA blank-team-name early exits map to `NOT_STORED` (not `teamsPendingRegistration`), because a
  real run cannot register a nameless team either.
- 2026-09-29: Tests. `MatchLifecyclePlannerTest` (decision table + `planCreation` + merge rule),
  `ImportPreviewClassificationTest` and `ImportPreviewResultDtoMapperTest` (domain records/DTO),
  JPA `findRoundStatusCounts` assertion, in-memory `findRoundStatusCounts` test, `PreviewChangeTest`
  (plan→change mapping), `IncrementalPreviewCollectorTest` (per-source buckets, teamsPending creation,
  regression/reschedule/identity-conflict projection, duplicate detection, processor-level read-only) and
  `IncrementalPreviewServiceTest` (wired FCTT snapshot: empty-store buckets/changes/progress, second-snapshot
  upgrades + completed round, duplicate `id_partido` counted once, unresolved fixture, all against a
  `MatchRepository` decorator that throws on any write). `mvn test` from the root passes (all 10 modules;
  api-runtime/import-runtime/api-rest Spring contexts still start with the new service dependencies).
- 2026-09-29: Documentation. Described the `classification` block in `tt-data-league-import-runtime/README.md`
  (new "Preview classification" subsection) and added a concise cross-reference in
  `tt-data-league-api-runtime/README.md` next to `roundProgress`. No schema change, so
  `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` is unchanged.
- 2026-09-29: Closed by explicit user approval; status `in-review` → `done`. All acceptance criteria
  checked in the registry and this file.
