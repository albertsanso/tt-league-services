# Build Plan
Source task: **T9** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.3 cross-check; gap G8; risk K4).

## Acceptance Criteria
- [x] A warning is reported when payload jornada is missing and the RFETM day-folder fallback is used
- [x] When a stored match has the same id_partido but a different natural key, an issue is raised and no duplicate match is created
- [x] Tests cover both cases

## Baseline (verified 2026-09-28, after FEAT-00084 / commit 50a0d0e)

- `RfetmMatchImportProcessor.resolveRound(Acta, MatchReportContext)` returns `acta.round()` (payload
  `jornada`) and otherwise falls back to `context.round()` (the day folder), logging only
  `LOGGER.warn`. The fallback never reaches the run's reported issues, so it is invisible in the
  traversal summary, metrics and run status (G8).
- FCTT and BCNESA take the round from their context and have no payload/folder fallback in the match
  processor; BCNESA's `Other`-group file-name fallback is T15 (G21) and out of scope.
- All three processors resolve `existing` with `MatchRepository.findMatchByNaturalKey(...)` and hand it
  to `MatchLifecycleWriter.apply(classification, existing, source)`. None of them calls
  `findBySourceFixtureId` (FEAT-00083 added the port and stores `id_partido`, BCNESA only on
  `fixtureIndex() == 0`).
- Without a guard, a jornada drift gives an empty natural-key lookup for an already stored fixture:
  the writer then calls `saveMatch` with the same `(source, source_fixture_id)`, which fails on
  `uk_match_source_fixture_id` in JPA (and in `InMemoryRepositories.saveMatch`) as a processor
  failure; for a legacy stored row with a `NULL` fixture id it silently creates a duplicate. An upgrade
  whose stored and incoming fixture ids differ overwrites the stored id (FEAT-00083 contract 5 left
  that to this feature).
- `ImportRunContext.recordMatchOutcome` counts every `MatchLifecycleOutcome` and turns reportable ones
  into `ImportExecutionIssue(processor, location, reason)` entries in `reportedMatchIssues()`, which
  `NavigatorImportExecutionService` publishes as warnings. `ImportRunStatusPolicy` never fails a run on
  these warnings. `ImportLifecycleCounters` (domain, FEAT-00082) has a fixed field set; no code
  switches exhaustively over `MatchLifecycleOutcome` outside the lifecycle package.

## Contracts

1. **Reported issue for the round fallback** (`shared/execution/ImportRunContext`):
   `public void recordRoundFallback(String processor, Path location, String reason)` appends an
   `ImportExecutionIssue` to `reportedMatchIssues` (warnings channel). It does not touch the outcome
   counters or `ImportLifecycleCounters`, and it never fails the run. Arguments are required
   (`Objects.requireNonNull` on `processor` and `reason`).
2. **RFETM fallback reporting** (`RfetmMatchImportProcessor.resolveRound`): when `acta.round()` is
   `null`, keep the existing `LOGGER.warn`, and call
   `context.runContext().recordRoundFallback(getClass().getSimpleName(), context.matchReportFile(),
   "No jornada in payload; round " + context.round() + " taken from the day folder " + context.day())`.
   The fallback round is still used (the analysis keeps it for legacy files); the behavior with a
   payload `jornada` is unchanged and records nothing.
3. **Fixture identity guard** (new `shared/match/lifecycle/MatchFixtureIdentityGuard`, plain final
   class constructed by each processor with its `MatchRepository`, like `MatchLifecycleWriter`):

   ```java
   public record IncomingFixture(ImportSource source, String sourceFixtureId, String competition,
                                 Season season, Integer groupNumber, int round, String phase) { }

   public Optional<String> conflict(IncomingFixture incoming, Optional<Match> naturalKeyMatch)
   ```

   `IncomingFixture` (nested record; `source`, `competition`, `season` required) carries the values the
   processor already used for `findMatchByNaturalKey` plus the fixture id it stores. The guard does not
   build a `Match` (a throwaway `createNew()` header would publish a `MatchCreatedEvent`). It returns a
   human-readable conflict reason, or empty when the write may proceed. Rules, in order:
   - `sourceFixtureId == null` (legacy file, BCNESA fixture index > 0) → empty; no lookup.
   - `byFixture = matchRepository.findBySourceFixtureId(source, sourceFixtureId)` (source-scoped).
   - `byFixture` present and `naturalKeyMatch` empty → conflict: the reason names the `id_partido`,
     the stored match id with its competition/group/round/phase, and the incoming
     group/round/phase, ending "not stored to avoid a duplicate fixture".
   - `byFixture` present, `naturalKeyMatch` present and their ids differ → conflict (two stored matches
     disagree; both ids in the reason).
   - `byFixture` empty and `naturalKeyMatch` present with a non-null `getSourceFixtureId()` different
     from the incoming id → conflict (same natural key, different `id_partido`; replaces FEAT-00083's
     "written as incoming" upgrade behavior).
   - Otherwise (same match found by both, natural-key match with a `null` stored fixture id, or nothing
     found by either) → empty.

   The guard never writes, never throws for a conflict, and lets repository failures propagate.
4. **Lifecycle outcome** (`MatchLifecycleOutcome`): new constant `FIXTURE_IDENTITY_CONFLICT` —
   "the acta's `id_partido` and natural key point at different stored matches; nothing was written".
   `isReportable()` returns `true` for it. It is counted in `matchOutcomeCounts()` but is **not** added
   to `ImportLifecycleCounters` (no domain/API change); it surfaces as a reported issue/warning.
5. **Processor wiring** (RFETM, FCTT, BCNESA `process`): right after `findMatchByNaturalKey`,
   build an `IncomingFixture` from the same local values (competition, season, group, round, phase)
   and the fixture id the processor stores, then
   ```java
   Optional<String> conflict = identityGuard.conflict(incoming, existing);
   if (conflict.isPresent()) {
       recordOutcome(context, MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT, conflict.get());
       return;
   }
   ```
   before `lifecycleWriter.apply`. The per-processor `recordOutcome` helpers take the reason string
   (callers of the writer pass `classification.reason()`), so the conflict reason reaches
   `recordMatchOutcome` and the existing `LOGGER.warn` for reportable outcomes. RFETM passes
   `phase = null` (as its natural-key lookup does) and `acta.matchId()`; FCTT passes `context.phase()`
   and `acta.matchId()`; BCNESA extracts its fixture-index rule into one private helper
   `sourceFixtureId(context, acta)` (`fixtureIndex() == 0 ? acta.matchId() : null`) used by both
   header builders and the guard, so the rule cannot diverge.
6. **Lifecycle writer** (`MatchLifecycleWriter.keepStoredFixtureId`): logic unchanged (a `null`
   incoming id still keeps the stored one). Update its javadoc: a differing non-null pair can no
   longer reach the writer because the guard reports it first.

## Implementation order

1. `MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT` + `isReportable()`.
2. `ImportRunContext.recordRoundFallback` (contract 1).
3. `MatchFixtureIdentityGuard` (contract 3) with class javadoc citing G8/G16/K4 and the rule table.
4. RFETM processor: `resolveRound` reporting (contract 2); guard wiring (contract 5).
5. FCTT and BCNESA processors: guard wiring (contract 5), including the BCNESA
   `sourceFixtureId(context, acta)` helper; an index > 0 fixture (fixture id `null`) skips the lookup.
6. `MatchLifecycleWriter` javadoc update (contract 6).
7. **Unit tests** (`tt-data-league-import/src/test/java/.../process/`):
   - New `MatchFixtureIdentityGuardTest` over `InMemoryRepositories.Matches`: null incoming id →
     empty and no lookup effect; same match by both lookups → empty; natural-key match with stored
     `null` fixture id → empty; nothing stored → empty; stored by fixture id + empty natural key →
     conflict naming the `id_partido` and both rounds; two different stored matches → conflict; natural-key match with a
     different non-null fixture id → conflict; same id under another `ImportSource` → empty
     (source scoping); `null` source/competition/season in `IncomingFixture` → `NullPointerException`.
   - `ImportRunContext` test (extend the existing context/policy test or add
     `ImportRunContextTest`): `recordRoundFallback` adds one issue with processor, location and reason,
     leaves `matchOutcomeCounts()` and `lifecycleCounters()` unchanged; `recordMatchOutcome` of
     `FIXTURE_IDENTITY_CONFLICT` adds a reported issue; `ImportRunStatusPolicy` still yields
     `SUCCESS` for a run whose only warnings are these.
8. **Processor tests** (`MatchLifecycleImportProcessorsTest`):
   - RFETM acta **without** payload `jornada` → match stored with the day-folder round and exactly one
     reported issue whose reason mentions the day folder; the same acta **with** `jornada` → no issue.
   - RFETM jornada drift: import a 2026-2027-style pending acta with `id_partido` X and `jornada` 3,
     then the same fixture (same teams, same X) with `jornada` 4 (or missing `jornada` in a day folder
     that differs) → still exactly one match (round 3, unchanged), outcome `FIXTURE_IDENTITY_CONFLICT`
     recorded once, a reported issue, no processor failure, no `saveMatch`/`replaceMatchContent`.
   - Same drift where the second version is PLAYED → no upgrade, stored match stays SCHEDULED.
   - Natural-key match whose stored fixture id differs from the incoming non-null id → conflict,
     stored fixture id unchanged (replaces the FEAT-00083 overwrite behavior; adjust any existing
     assertion that relied on it).
   - FCTT and BCNESA: one drift case each (same `id_partido`, different round) → one match, conflict
     reported. BCNESA fixture index 1 (no fixture id) is unaffected.
   - Regression: the existing `rfetmIdPartidoIsStoredOnCreateAndThePublishedHeaderIsWrittenOnUpgrade`,
     `fcttUnpublishedAndPublishedVersionsShareOneMatchAndOneFixtureId` and BCNESA fixture-id tests keep
     passing unchanged (consistent ids → guard returns empty).
9. **Documentation.** No schema change, so `rfetm-datamodel.md` is untouched. No CLI/configuration
   change, so no README update is required; if `tt-data-league-import/README.md` lists the reported
   match-lifecycle warnings, add the round-fallback warning and `FIXTURE_IDENTITY_CONFLICT` there.
10. **Validation.** `mvn -pl tt-data-league-import -am test`, then the full `mvn test` from the root.

# Implementation Guidelines

- Affected modules: import only (main and test). No domain, JPA, schema, runtime or API change.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- The natural key stays the primary identity for the write path; `id_partido` is only a consistency
  check. Every fixture-id lookup goes through `findBySourceFixtureId(ImportSource, String)`; never an
  unscoped lookup, never parse or derive `id_partido`.
- A conflict is a reported warning with no write, not a thrown exception or a processor failure, and
  never an automatic merge, re-key or delete of the stored match (fixing a drifted stored match is a
  manual decision).
- Keep the day-folder fallback itself: legacy RFETM files need it. Only make it visible.
- Do not construct a `Match` with `createNew()` just to feed the guard (it publishes a
  `MatchCreatedEvent`); pass the key values explicitly.
- Out of scope: a new `ImportLifecycleCounters` field or API exposure for conflicts, BCNESA `Other`
  group file-name fallback (FEAT-00091 / T15), snapshot reconciliation (FEAT-00086), duplicate
  `id_partido` inside one upload (FEAT-00087), amended actas (FEAT-00089), and backfilling fixture ids
  on stored rows (FEAT-00083 open question 1).

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 3: visibility and safety. Depends on: FEAT-00081 (T6), FEAT-00083 (T18).
- 2026-09-28: Build plan written against the code after FEAT-00084 (commit 50a0d0e); status `idea` →
  `planned`. Decisions:
  - The cross-check lives in a shared `MatchFixtureIdentityGuard` called by all three processors before
    `MatchLifecycleWriter.apply`, not inside the writer, so the writer's decision table and the
    `MatchLifecycleSource` interface stay unchanged and the conflict carries its own reason.
  - A conflict is a new reportable `MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT`; the round fallback
    is a reported issue via `ImportRunContext.recordRoundFallback`. Both are warnings: the run status
    policy is unchanged, and `ImportLifecycleCounters` is not extended.
  - The guard also rejects "same natural key, different non-null `id_partido`", closing the upgrade
    overwrite that FEAT-00083 deferred to this feature; `keepStoredFixtureId` stays for `null` ids.
  - Without the guard, a drifted fixture with a stored `id_partido` fails on
    `uk_match_source_fixture_id` as a processor failure, and one whose stored row has a `NULL` fixture
    id is silently duplicated; the guard turns the first into a reported issue. The second remains
    undetectable here (no stored id to compare) and is left to FEAT-00086 reconciliation.
- Open question (recommendation: keep as warning): should `FIXTURE_IDENTITY_CONFLICT` fail the run via
  `hasIssues` instead of being a warning? The plan follows FEAT-00082's rule that reported match
  outcomes never fail the run or skip consolidation.
- 2026-09-29: Executed the build plan; status `planned` → `in-progress` → `in-review`. All contracts 1-6
  implemented as written (guard, `recordRoundFallback`, `FIXTURE_IDENTITY_CONFLICT`, three processor
  wirings, BCNESA `sourceFixtureId(context, acta)` helper, writer javadoc). Validation:
  `mvn -pl tt-data-league-import -am test` (331 tests) and full-reactor `mvn test` both pass.
  Acceptance criteria verified by `MatchFixtureIdentityGuardTest`, `ImportRunContextTest` and the new
  `MatchLifecycleImportProcessorsTest` cases (fallback warning with/without payload jornada; RFETM,
  FCTT and BCNESA same-id/different-key drift; played-second-version no-upgrade; BCNESA index > 0).
  Deviations from step 8's regression list: the RFETM reference pair
  (`acta_rfetm_2026_unpublished`/`published`) carries *different* `id_partido` values for the same
  natural key, so the two existing upgrade tests could not keep passing unchanged; per step 8's
  mismatch bullet their published acta is now aligned to the stored fixture id
  (`withMatchId(...)`, same in `NavigatorImportExecutionLifecycleTest.publishedWithPendingTeams()`),
  and the original mismatched-id pair became the new conflict test
  `rfetmPublishedActaWithADifferentIdPartidoConflictsAndNeverOverwritesTheStoredOne`, which replaces
  the FEAT-00083 "incoming id written as is" assertion as planned. No README/schema change: the module
  has no README warning list and the data model is untouched.
