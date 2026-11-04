# Build Plan
Source task: **T10** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.7; risk K8).

## Acceptance Criteria
- [x] After a snapshot run, stored SCHEDULED matches of the season not seen in the run are reported, matched by id_partido or natural key
- [x] Rounds beyond the snapshot's highest round are not flagged (FCTT sliding window)
- [x] No match is deleted or modified by reconciliation
- [x] Tests cover a vanished fixture and the FCTT window case

## Baseline (verified 2026-09-29, after FEAT-00085 / commit 773ae14)

- Every run is a snapshot run today: with `--season` (CLI) or a resource season (API) the navigators
  traverse the whole season folder, which the upload replaces as a unit. Delta mode is FEAT-00090 and
  does not exist yet.
- `NavigatorImportExecutionService.execute` is the single execution path (CLI `App.run`, API
  `NavigatorBackedImportResourceProcessService.process`). It already holds a nullable
  `MatchRepository` (FEAT-00084) and reads `findRoundProgress` only when the request has a season.
- The three match processors (`RfetmMatchImportProcessor`, `FcttMatchImportProcessor`,
  `BcnesaMatchImportProcessor`) compute competition / group / round / phase, resolve both `Team`s
  (silently returning when a team is not registered for the season), call
  `findMatchByNaturalKey`, run `MatchFixtureIdentityGuard` and then `MatchLifecycleWriter.apply`.
  RFETM stores `groupNumber = acta.group() ?: 0` and `phase = null`; BCNESA's fixture id comes from
  `sourceFixtureId(context, acta)` (index 0 only); FCTT uses `acta.matchId()`.
- `ImportRunContext` is per-run state; its `reportedMatchIssues()` become `ImportExecutionResult.warnings`,
  which the CLI prints in the `import finished` line and the API maps to `warning` findings. Warnings
  never change the run status (`ImportRunStatusPolicy`).
- `MatchRepository` has no "stored matches of a source/season/status" query; its implementors are
  `MatchRepositoryJpa` and `InMemoryRepositories.Matches` only. `match_record` already has
  `idx_match_source_season_competition_status`, so no schema change is needed.
- Stored SCHEDULED rows created before FEAT-00083 have `source_fixture_id = NULL`; only the natural key
  can match them.

## Contracts

1. **Domain port** (`MatchRepository`):
   `List<Match> findMatchesBySourceSeasonAndStatus(ImportSource source, Season season, MatchStatus status);`
   abstract (no default), all arguments required (`NullPointerException`), source-scoped, read-only,
   returns an empty list when nothing matches. Javadoc: used by snapshot reconciliation (FEAT-00086).
2. **JPA**: `MatchRepositoryHelper.findAllBySourceAndSeasonAndStatus(Source source, String season,
   MatchStatus status)` (derived query, JPA enum); `MatchRepositoryJpa` implementation maps
   `Source.valueOf(source.name())`, `season.toString()`, `MatchStatus.valueOf(status.name())` and
   `matchJPAToMatchMapper`. No entity, index or column change.
3. **Seen-fixture ledger** (`ImportRunContext`): new method
   ```java
   public void recordSnapshotFixture(String competition, Integer groupNumber, String phase, int round,
                                     String sourceFixtureId, UUID homeTeamId, UUID awayTeamId)
   ```
   `competition` required; `sourceFixtureId` nullable; team ids nullable but both-or-neither
   (`IllegalArgumentException` otherwise). It accumulates, per run: the set of non-null fixture ids;
   the set of natural keys `(competition, groupNumber, phase, round, homeTeamId, awayTeamId)` when both
   team ids are present; and the highest round per scope `(competition, groupNumber, phase)` (null-safe).
   Exposed as an immutable `SnapshotFixtures snapshotFixtures()` value (new record in
   `shared/execution`, with `boolean isEmpty()`, `boolean containsFixtureId(String)`,
   `boolean containsNaturalKey(Match)`, `OptionalInt highestRound(String competition, Integer groupNumber,
   String phase)` and `OptionalInt highestRoundOverall()`). It never touches the outcome counters,
   lifecycle counters or reported issues.
4. **Processor wiring** (RFETM, FCTT, BCNESA `process`): once competition, group, round and phase are
   known and both team lookups have been attempted, call `recordSnapshotFixture(...)` with the same
   values the natural-key lookup uses and the same fixture id the processor stores (BCNESA via
   `sourceFixtureId(context, acta)`), passing the team ids when both teams resolved and `null`/`null`
   otherwise. This happens **before** the unresolved-team early return, the identity guard and the
   writer, so fixtures with an unregistered team (fixture id and round still count) and
   `FIXTURE_IDENTITY_CONFLICT` fixtures are both "seen". RFETM computes `round` (and its fallback
   warning) before the team check so it can be recorded; its behavior is otherwise unchanged. Actas with
   no payload, and navigator-level skips (unresolved pending placeholders without teams), record nothing.
5. **Reconciler** (new `shared/execution/SnapshotReconciler`, plain final class, constructor
   `SnapshotReconciler(MatchRepository)`):
   ```java
   public List<ImportExecutionIssue> reconcile(ImportSource source, Season season, SnapshotFixtures seen)
   ```
   - `seen.isEmpty()` → empty list, no repository call.
   - Reads `findMatchesBySourceSeasonAndStatus(source, season, SCHEDULED)` once.
   - A stored match is **seen** when its non-null `sourceFixtureId` is in the seen fixture ids, or its
     natural key is in the seen natural keys.
   - **Window rule:** an unseen match is flagged only when `round <= limit`, where `limit` is
     `seen.highestRound(competition, groupNumber, phase)` for its scope, or `seen.highestRoundOverall()`
     when the scope has no seen fixture at all (a whole group/phase that vanished is still reported,
     unless all its stored rounds are beyond the snapshot's overall highest round).
   - Each flagged match becomes one `ImportExecutionIssue("SnapshotReconciliation", "match " + id,
     reason)`, reason e.g. `Stored SCHEDULED match absent from the 2026-2027 snapshot: <competition>
     G<group> <phase> round <r>, <home> vs <away>, id_partido <id|none>; kept, not deleted`. Output is
     sorted by competition, group, phase, round, home team name (nulls last) for deterministic logs.
   - Never writes: it only calls the read port above. Repository failures propagate.
6. **Execution service** (`NavigatorImportExecutionService.execute`): right after the traversal status is
   computed and before post-processing, when `status == SUCCESS`, the request has a season and
   `matchRepository != null`, run the reconciler over `runContext.snapshotFixtures()` and append its
   issues to the warnings list returned in `ImportExecutionResult.warnings` (after
   `runContext.reportedMatchIssues()`). A `RuntimeException` is handled like round progress: an
   `ImportExecutionIssue("snapshot-reconciliation", "", message)` and status `FAILURE`. Runs without a
   season, with processor failures/issues (an incomplete traversal would produce false "absent"
   reports), or with `EMPTY_RESULT` skip reconciliation. Reconciliation results never change a
   `SUCCESS` status.

## Implementation order

1. Domain port method + javadoc (contract 1).
2. JPA helper query and `MatchRepositoryJpa` implementation (contract 2).
3. JPA test (extend `MatchSourceFixtureIdJpaTest` or add `MatchByStatusJpaTest` in the same style):
   returns only the requested source, season and status; another source with the same season is excluded;
   empty list when nothing matches; `null` arguments → `NullPointerException`.
4. `InMemoryRepositories.Matches.findMatchesBySourceSeasonAndStatus` (filter `saved`), with a case in
   `InMemoryMatchRepositoryTest`.
5. `SnapshotFixtures` record and `ImportRunContext.recordSnapshotFixture` / `snapshotFixtures()`
   (contract 3); cases in `ImportRunContextTest`: fixture id and natural key recorded; highest round per
   scope and overall (null group/phase scopes are distinct and null-safe); one team id without the other
   → `IllegalArgumentException`; outcome counters, lifecycle counters and reported issues untouched.
6. Processor wiring for RFETM, FCTT and BCNESA (contract 4).
7. `SnapshotReconciler` (contract 5) with class javadoc citing T10/K8 and the window rule; new
   `SnapshotReconcilerTest` over `InMemoryRepositories.Matches`:
   - vanished fixture: stored SCHEDULED round 1 with fixture id X, snapshot sees other round-1 fixtures
     of the scope but not X → one issue naming the match id, round, teams and `id_partido X`;
   - seen by fixture id only (natural key drifted) → not flagged; seen by natural key only (stored
     `source_fixture_id` null, legacy row) → not flagged;
   - FCTT window: stored SCHEDULED rounds 1–3 of `tercera-nacional` G1, snapshot sees rounds 1–2 → round 3
     not flagged; a round-2 fixture missing from the snapshot → flagged;
   - scope absent from the snapshot: flagged when its round ≤ overall highest round, not flagged beyond it;
   - PLAYED stored matches are never flagged; other source or season never read;
   - empty `SnapshotFixtures` → empty list and no repository call;
   - the repository sees no `saveMatch`, `replaceMatchContent` or `updateSchedule` (use a recording
     wrapper or assert `saved` unchanged: same ids, statuses and schedules before and after).
8. Execution service wiring (contract 6).
9. Integration tests (`NavigatorImportExecutionLifecycleTest`, in-memory repositories, real FCTT
   2026-2027-style fixture files as in the existing lifecycle tests):
   - first run imports jornadas 1–2 of a group; second run with one jornada-2 fixture removed from the
     season folder → exactly one `SnapshotReconciliation` warning for it, status `SUCCESS`, the stored
     match still present and SCHEDULED with its schedule unchanged;
   - FCTT window: seed a stored round-3 SCHEDULED match of the same group, then import a snapshot holding
     only jornadas 1–2 → no warning for round 3;
   - run without a season, and a run with a processor failure → no reconciliation warnings;
   - an RFETM case where an acta's team is not registered: its stored SCHEDULED match (same fixture id)
     is not flagged.
   - Existing `NavigatorImportExecutionServiceTest` / lifecycle assertions on exact warning lists keep
     passing (first-run imports see every stored fixture, so no reconciliation warning appears); adjust
     only if a test imports into a pre-seeded season on purpose.
10. **Documentation.** `tt-data-league-import-runtime/README.md`: new "Snapshot reconciliation" subsection
    next to "Jornada progress" — when it runs (successful run with a season), what is reported, the
    window rule, report-only (never deletes or modifies; deletion stays manual), and that it surfaces as
    warnings in the run log and as `warning` findings in the administration API. No schema change, so
    `rfetm-datamodel.md` is untouched (the new query uses the existing
    `idx_match_source_season_competition_status`).
11. **Validation.** `mvn -pl tt-data-league-import -am test`, then the full `mvn test` from the root.

# Implementation Guidelines

- Affected modules: domain (one read port), JPA (one read query), import (main and test), import-runtime
  README. No schema, API DTO, CLI argument or configuration change; the runtimes already inject
  `MatchRepository` into `NavigatorImportExecutionService`.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- Report only: reconciliation never deletes, re-keys, re-statuses or reschedules a match, and never feeds
  the run status policy beyond the "reconciliation itself threw" failure. Deletion of vanished fixtures
  stays a manual decision (analysis 4.7, K8).
- Matching uses the exact values the processors already store: `id_partido` only through the seen set
  (never parsed or derived), and the natural key exactly as `findMatchByNaturalKey` uses it (RFETM group
  `0` default, RFETM `phase = null`). No unscoped lookups.
- Only SCHEDULED stored matches are considered; PLAYED matches absent from a snapshot are out of scope.
- Out of scope: delta uploads (FEAT-00090 must skip reconciliation for delta runs, since a delta is not a
  full season), the shrink check (FEAT-00087), a dedicated counter or API/DTO field for reconciliation
  results, a lower-bound window rule, and any automatic cleanup.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 3: visibility and safety. Depends on: FEAT-00081 (T6), FEAT-00083 (T18).
- 2026-09-29: Build plan written against the code after FEAT-00085 (commit 773ae14); status `idea` →
  `planned`. Decisions:
  - "Seen" is recorded per run in `ImportRunContext` by the processors, before team resolution, the
    identity guard and the writer, so unregistered-team fixtures and `FIXTURE_IDENTITY_CONFLICT`
    fixtures are not falsely reported as absent. FEAT-00085 is added as a dependency: it defines the
    guard and the BCNESA `sourceFixtureId` helper the wiring reuses, and it left legacy `NULL`-fixture-id
    duplicates to this feature.
  - Results reuse the existing warnings channel (`ImportExecutionResult.warnings` → CLI log, API
    `warning` findings) instead of a new result/DTO field, keeping the feature small.
  - Reconciliation runs only on a `SUCCESS` traversal with a season: a failed or partial traversal would
    report present fixtures as absent.
  - Window rule is per `(competition, group, phase)` scope, with the snapshot-wide highest round for
    scopes that vanished entirely, so a disappeared group is still reported.
- Open question 1 (recommendation: keep upper bound only): should rounds *below* the snapshot's lowest
  round per scope also be exempt, in case FCTT's window drops old jornadas? A stored SCHEDULED match of
  an old round that the federation stopped exporting never got a played acta, so reporting it is useful;
  revisit if FCTT windows produce noise (analysis open question 2).
- Open question 2: a legacy stored SCHEDULED row (`source_fixture_id` NULL) whose fixture now has an
  unregistered team cannot be matched (no fixture id stored, no natural key) and will be reported; this is
  rare and acceptable for a report.
- 2026-09-29: Plan approved by the user; status `planned` → `ready`. Open questions 1 and 2 stay as
  recommended (upper-bound window only; the rare unmatched legacy row is reported).
- 2026-09-29: Implemented per the build plan; status `ready` → `in-progress` → `in-review`. Delivered:
  - Domain port `MatchRepository.findMatchesBySourceSeasonAndStatus` (abstract, NPE on null args),
    JPA `MatchRepositoryHelper.findAllBySourceAndSeasonAndStatus` + `MatchRepositoryJpa` mapping
    (no schema change; uses `idx_match_source_season_competition_status`), and the in-memory
    `Matches` filter.
  - `SnapshotFixtures` record and `ImportRunContext.recordSnapshotFixture` / `snapshotFixtures()`
    ledger (fixture ids, natural keys, highest round per scope; counters and issues untouched).
  - Processor wiring in RFETM, FCTT and BCNESA: `recordSnapshotFixture` runs before the
    unresolved-team early return, the identity guard and the writer. RFETM now computes `round`
    (and its fallback warning) before the team check, as the plan called for.
  - `SnapshotReconciler` (report-only, window rule, deterministic ordering) and the execution-service
    wiring: on a `SUCCESS` run with a season and a wired `MatchRepository`, its findings append to
    `warnings`; a reconciliation throw yields a `snapshot-reconciliation` issue and `FAILURE`.
  - Docs: `tt-data-league-import-runtime/README.md` gained a "Snapshot reconciliation" subsection.
  - Tests: `MatchByStatusJpaTest`, `InMemoryMatchRepositoryTest`, `ImportRunContextTest`,
    `SnapshotReconcilerTest` (vanished fixture, seen-by-id/natural-key, FCTT window, vanished scope,
    PLAYED/other-source/season excluded, empty-seen no-read, never-writes), the RFETM
    unregistered-team seen case in `MatchLifecycleImportProcessorsTest`, and four
    `NavigatorImportExecutionLifecycleTest` integration cases (vanished jornada, sliding window,
    no-season skip, processor-failure skip). Validation: full `mvn test` reactor is green.
- 2026-09-29: Closed on explicit user request; status `in-review` → `done`. All four acceptance
  criteria verified against the delivered behavior above.
