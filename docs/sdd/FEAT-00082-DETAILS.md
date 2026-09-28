# Build Plan
Source task: **T7** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.8; gaps G5, G10; risk K12).

## Acceptance Criteria
- [x] Traversal summaries, ImportExecutionMetrics and ImportProcessResult carry scheduledCreated, upgradedToPlayed, rescheduled, partialActas, invalidActas and unresolvedPendingFixtures
- [x] A run with no changes (for example all actas pending and already stored) ends SUCCESS/PROCESSED; EMPTY_RESULT is kept for no actas found
- [x] ImportResource.lastProcessedDate is set when a run finishes
- [x] README documents the status change and counters

## Baseline (verified 2026-09-28, after commit 199f5f7 / FEAT-00081)

- FEAT-00081 records one `MatchLifecycleOutcome` per acta/fixture on `ImportRunContext`
  (`recordMatchOutcome`, `matchOutcomeCounts()`, `reportedMatchIssues()`), but nothing reads them.
  Outcomes are mutually exclusive per acta: a PARTIAL or INVALID acta that creates or reschedules a
  `SCHEDULED` match is counted only as `PARTIAL_REPORTED` / `INVALID_REPORTED`, not also as
  `SCHEDULED_CREATED` / `RESCHEDULED`.
- Unresolved pending fixtures are counted only as generic skips:
  - FCTT: `FcttActasDirectoryNavigator` → `counters.skipped++` when
    `classifier.classify(acta).unresolvedPendingFixture()`.
  - BCNESA: `BcnesaActasDirectoryNavigator.dispatchFixture` → `fixturesUnresolved++` for every
    unattributable fixture, played or not.
  - RFETM: `RfetmActasDirectoryNavigator` → `skipped++` when a side has neither id nor name.
- `TraversalSummary(filesSeen, dispatched, skipped, processorFailures, issues)` and
  `BcnesaTraversalSummary(...)` have custom `equals`/`hashCode`/`toString` that exclude `issues`.
  `ImportExecutionMetrics(filesSeen, itemsDispatched, skipped, processorFailures, persistenceWrites, elapsedMillis)`.
  `ImportProcessResult` (domain, 11 components plus a 7-arg convenience constructor).
- Status is computed twice, with the same rule
  `failures/issues → FAILURE; dispatched == 0 → EMPTY_RESULT; else SUCCESS`:
  in `NavigatorImportExecutionService.execute` and in `App.AppSupport.result`
  (import runtime). Consolidation runs only on `SUCCESS`.
- `StartImportProcessCommandHandler.runAsync` calls `resource.finishProcessing(status == SUCCESS)`, so
  `EMPTY_RESULT` ends the resource as `ERROR` (G10). `ImportResource.lastProcessedDate` is a `final`
  `Optional` that `finishProcessing` never sets (G5). The JPA column `last_processed_date` and both
  mappers already exist, so no schema change is needed.
- `NavigatorBackedImportResourceProcessService.process` maps `ImportExecutionResult` to
  `ImportProcessResult` with `findings = List.of()`. `ImportProcessResultDtoMapper` and
  `ImportRunStatusDtoMapper.toResultDto` map it to `ImportProcessResultDto` (flat fields).
- After FEAT-00081, an all-pending run already dispatches every fixture (dispatched > 0), so the
  measured G10 case (BCNESA 2026-2027) is already `SUCCESS` at execution level. The remaining
  `dispatched == 0` cases are: no files, all files skipped (parse/structure), and all fixtures
  unresolved placeholders (FCTT pre-season).

## Contracts

1. **Domain value** `org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters`
   (record in core-domain, so both the import module and `ImportProcessResult` use one type):
   `(long scheduledCreated, long upgradedToPlayed, long rescheduled, long partialActas,
   long invalidActas, long unresolvedPendingFixtures)`. Components must not be negative. Provides
   `static ImportLifecycleCounters ZERO`, `plus(ImportLifecycleCounters)`, and `hasActivity()`
   (any component > 0).
2. **Import module mapping** `ImportRunContext`:
   - `recordUnresolvedPendingFixture(String navigator, Path location, String reason)`: increments an
     internal counter and adds an `ImportExecutionIssue` to `reportedMatchIssues()`. Keep the
     accessor name and update its javadoc to say it also carries unresolved fixtures.
   - `lifecycleCounters()`: builds `ImportLifecycleCounters` from `matchOutcomeCounts()`
     (`SCHEDULED_CREATED`, `UPGRADED_TO_PLAYED`, `RESCHEDULED`, `PARTIAL_REPORTED`,
     `INVALID_REPORTED`) and the unresolved counter. Update the FEAT-00081 javadoc on
     `recordMatchOutcome` ("informational only … is FEAT-00082").
3. **Summaries and metrics** (append a component; keep the existing convenience constructors,
   defaulting to `ImportLifecycleCounters.ZERO`, so untouched callers compile):
   - `TraversalSummary(..., List<ImportExecutionIssue> issues, ImportLifecycleCounters lifecycle)`.
   - `BcnesaTraversalSummary(..., issues, ImportLifecycleCounters lifecycle)`.
   - `ImportExecutionMetrics(..., elapsedMillis, ImportLifecycleCounters lifecycle)`, with a 6-arg
     constructor that defaults to `ZERO`.
   - Include `lifecycle` in `equals`, `hashCode` and `toString` of both summaries (it is part of what
     the traversal did); `issues` stays excluded as today. A null `lifecycle` is normalised to `ZERO`.
4. **Result** `ImportProcessResult` gains a trailing `ImportLifecycleCounters lifecycle` component
   (null → `ZERO`). The existing 11-arg shape stays as a convenience constructor, and the 7-arg
   constructor plus `success/empty/failure` factories keep working. `ImportProcessResultDto` gains
   six flat `long` fields with the same names. Both DTO mappers fill them.
5. **Warnings channel** `ImportExecutionResult` gains `List<ImportExecutionIssue> warnings` (null →
   empty; keep a constructor without it). `NavigatorImportExecutionService` fills it from
   `runContext.reportedMatchIssues()`. `NavigatorBackedImportResourceProcessService` maps each
   warning to an `ImportPreviewFinding("warning", processor + ": " + message, location)` in
   `ImportProcessResult.findings`. Warnings never affect status.
6. **Status rule**: one static policy in the import module,
   `org.cttelsamicsterrassa.data.load.shared.execution.ImportRunStatusPolicy.statusOf(long processorFailures,
   boolean hasIssues, long dispatched, ImportLifecycleCounters lifecycle)`:
   - `processorFailures > 0 || hasIssues` → `FAILURE` (unchanged; reported outcomes are not issues).
   - else `dispatched == 0 && lifecycle.unresolvedPendingFixtures() == 0` → `EMPTY_RESULT`
     ("no actas found": no files, or no file could be read as an acta).
   - else → `SUCCESS`. This includes runs where every outcome is `UNCHANGED` / `PLAYED_KEPT`, and
     runs whose only actas are unresolved placeholders.
   Both `NavigatorImportExecutionService` and `App.AppSupport` call it (no duplicated rule).
7. **Domain lifecycle**: replace `ImportResource.finishProcessing(boolean)` with
   `finishProcessing(boolean isValid, ZonedDateTime finishedAt)`. It sets `status` and
   `lastProcessedDate = Optional.of(finishedAt)` (field becomes non-final; `finishedAt` is required).
   Its only callers are the three calls in `StartImportProcessCommandHandler`, which all pass
   `ZonedDateTime.now(clock)`. The handler gains a constructor that takes a `java.time.Clock`. The
   existing `@Inject` constructor delegates with `Clock.systemDefaultZone()`, so no new Spring bean
   is needed. The timestamp is set on every terminal path (success, empty, failure, submission
   rejection, unexpected exception).
8. **Resource status**: `SUCCESS` → `PROCESSED`, and `FAILURE` / `EMPTY_RESULT` → `ERROR`, as
   today. The change is in which runs are `SUCCESS` (item 6), not in the mapping.

## Implementation order

1. **Domain** (`tt-data-league-core-domain`):
   - Add `ImportLifecycleCounters` and a `ImportLifecycleCountersTest` (ZERO, plus, negative
     rejection, hasActivity).
   - Extend `ImportProcessResult` (item 4), `ImportProcessResultDto`, `ImportProcessResultDtoMapper`
     and `ImportRunStatusDtoMapper.toResultDto`.
   - `ImportResource.finishProcessing(boolean, ZonedDateTime)` (item 7) and the handler `Clock`
     constructor. Tests in `StartImportProcessCommandHandlerTest`: a fixed clock sets
     `lastProcessedDate` on success, empty result, failure, a thrown runtime exception, and a
     rejected submission; `SUCCESS` → `PROCESSED`; `EMPTY_RESULT` → `ERROR`. Add
     `ImportResourceTest` cases, or extend the existing ones, for `finishProcessing` setting the date
     and still rejecting a non-`PROCESSING` state. Assert in `FindImportRunStatusQueryHandlerTest`
     that the counters reach the DTO.
2. **Run context and navigators** (`tt-data-league-import`):
   - `ImportRunContext.recordUnresolvedPendingFixture` and `lifecycleCounters()` (item 2).
   - FCTT navigator: in the existing placeholder branch, also call
     `runContext.recordUnresolvedPendingFixture(...)`. Keep `skipped++` so the skipped count and progress
     are unchanged.
   - BCNESA navigator: in `dispatchFixture`, when `!fixture.isResolved()`, classify
     `classifier.classify(acta, fixture.games())`. When it is `unresolvedPendingFixture()`, record
     it. `fixturesUnresolved++` stays for every unresolved fixture.
   - RFETM navigator: in the "neither id nor name" branch, classify the acta. When it is
     `unresolvedPendingFixture()`, record it. `skipped++` stays.
   - Each navigator's `Counters.toSummary` passes `runContext.lifecycleCounters()`. The overloads
     that build their own `ImportRunContext` still work.
3. **Summaries/metrics/result** (item 3, item 5): extend the records and constructors. Update
   `NavigatorImportExecutionService.traverse` (`Counts` gains `lifecycle`), `execute` (metrics with
   lifecycle, `warnings` from the run context, status via `ImportRunStatusPolicy`), `countsZero`, and
   `NavigatorBackedImportResourceProcessService.process` (lifecycle into `ImportProcessResult`,
   warnings into findings).
4. **Status policy** (item 6): add `ImportRunStatusPolicy` with `ImportRunStatusPolicyTest`, a table
   over failures, issues, dispatched and unresolved.
5. **Import runtime** (`tt-data-league-import-runtime/.../App.java`): `AppSupport.result` takes the
   summary's `lifecycle`, builds metrics with it and uses `ImportRunStatusPolicy`. The final
   `LOGGER.info("{} import finished: {}", ...)` now prints the counters through the `toString`
   of the metrics record. Update `AppTest` constructor usages.
6. **Tests** (import module):
   - `NavigatorImportExecutionServiceTest`: (a) the same pending snapshot imported twice →
     second run `SUCCESS`, `scheduledCreated == 0`, `rescheduled == 0`, and consolidation still
     runs; (b) a pending then published snapshot → `upgradedToPlayed == n`; (c) an empty folder →
     `EMPTY_RESULT`; (d) a folder with only unparsable files → `EMPTY_RESULT`; (e) an FCTT folder with
     only the `acta_fctt_2026_no_team_placeholder` fixture → `SUCCESS`, `unresolvedPendingFixtures == 1`;
     (f) a PARTIAL / INVALID / regression acta → `SUCCESS`, the matching counters set, and one
     warning each, with no issue.
   - Navigator tests (`RfetmActasDirectoryNavigatorTest`, `FcttActasDirectoryNavigatorTest`,
     `BcnesaActasDirectoryNavigatorTest`): the summary carries the lifecycle counters, and the
     placeholder paths record `unresolvedPendingFixtures`. Fix equality assertions that now include
     `lifecycle`.
   - `NavigatorBackedImportResourceProcessServiceTest`: counters are copied into
     `ImportProcessResult`, and warnings become `warning` findings.
   - Use the FEAT-00075 fixtures in `src/test/resources/actas/` and the wired in-memory repositories
     already used by `MatchLifecycleImportProcessorsTest`.
7. **Documentation**:
   - `tt-data-league-import-runtime/README.md`: a "Run status and counters" subsection (next to the
     FEAT-00081 paragraph). It lists the six counters and their per-acta exclusivity, the status
     rule (no-change runs are `SUCCESS` and the resource becomes `PROCESSED`; `EMPTY_RESULT` only
     when no acta was found), that reported outcomes are warnings and do not fail the run or skip
     consolidation, and that `lastProcessedDate` is set at the end of every run. Call out K12: some
     runs that used to end `ERROR` now end `PROCESSED`.
   - `tt-data-league-api-runtime/README.md`: one sentence in the async import paragraph saying that the
     terminal result carries the counters and warning findings.
   - `rfetm-datamodel.md`: no change unless the `import_resource.last_processed_date` description
     says it is unused. No schema change.
8. **Validation**: `mvn -pl tt-data-league-import-runtime -am test`, then the full `mvn test`. Compare
   against the recorded pre-existing failures (4 `InitialUserProvisioningServiceTest` and 8 import
   processor tests; see FEAT-00081 notes). Review the diff.

# Implementation Guidelines

- Affected modules: import, domain, runtime.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- Keep the dependency direction: the counter value lives in core-domain; the import module maps
  `ImportRunContext` into it; runtimes only wire and log. Compute run status only in
  `ImportRunStatusPolicy`. Do not copy the rule into `App` or the API runtime.
- Reported outcomes (PARTIAL, INVALID, regression, unresolved placeholder) are warnings. They must
  not become `ImportExecutionIssue`s in `issues`, because that would turn the run into `FAILURE` and
  skip consolidation. Genuine processor failures and traversal errors still fail the run.
- Do not relabel parse/structure skips as success. A run where no file could be read as an acta
  stays `EMPTY_RESULT`.
- All record changes are additive. Keep the existing convenience constructors so that
  preview, API and test callers outside this change compile unchanged.
- Out of scope: frontend display of the new counters (the DTO fields are additive and the
  existing UI ignores them), jornada progress (FEAT-00084), persisting run history, and the unused
  `ImportProcessCollector` / `*ProcessRecordingProcessor` classes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size S, Slice 2: incremental import. Depends on: FEAT-00081 (T6).
- 2026-09-28: Build plan written; status `planned`. FEAT-00081 is done (commit 199f5f7).
  - Decision: one core-domain record `ImportLifecycleCounters` is shared by the summaries, metrics
    and `ImportProcessResult`, instead of six loose fields per type. The DTO stays flat for JSON.
  - Decision: the counters mirror FEAT-00081's per-acta exclusive outcomes, so `scheduledCreated`
    excludes PARTIAL/INVALID creations and the sum of the counters never double-counts an acta.
    `REGRESSION_REPORTED` has no counter (not in the AC); regressions surface as warnings.
  - Decision: "no actas found" means nothing was dispatched **and** no unresolved pending fixture
    was recognised. An FCTT placeholder-only run is `SUCCESS`; an all-unparsable run stays
    `EMPTY_RESULT`.
  - Decision: `EMPTY_RESULT` still ends the resource as `ERROR`. Only the set of `SUCCESS` runs grows
    (K12), which keeps "no actas" visible to operators.
  - Decision: `lastProcessedDate` is set on every terminal path, including failures, through
    `finishProcessing(boolean, ZonedDateTime)` and a `Clock` in the handler for testability.
  - Open question: should the frontend import panel show the counters and warning findings?
    Warning findings may already render through the generic findings list. Counters would need a
    follow-up UI change.
- 2026-09-28: Implemented; status `in-review`.
  - Delivered as planned: `ImportLifecycleCounters` (core-domain) shared by `TraversalSummary`,
    `BcnesaTraversalSummary`, `ImportExecutionMetrics` and `ImportProcessResult`; the six flat
    `long` fields on `ImportProcessResultDto` filled by both DTO mappers;
    `ImportRunContext.recordUnresolvedPendingFixture` and `lifecycleCounters()`; unresolved recording
    in the FCTT, BCNESA (classified per fixture games) and RFETM (classified on the neither-id-nor-name
    skip) navigators; the `ImportExecutionResult.warnings` channel filled from
    `reportedMatchIssues()` and mapped to `warning` findings by
    `NavigatorBackedImportResourceProcessService`; the single `ImportRunStatusPolicy` used by
    `NavigatorImportExecutionService` and `App.AppSupport`; `finishProcessing(boolean, ZonedDateTime)`
    with the handler `Clock` constructor setting `lastProcessedDate` on every terminal path.
  - Test-infrastructure adjustment: `InMemoryRepositories.Matches/Lineups/Games/SetScores/DoublesPairs`
    and their `saved` fields/constructors became `public` so the lifecycle test in
    `load.shared.execution` can reuse them (test module only).
  - Tests: `ImportLifecycleCountersTest`, `ImportResourceTest` (new), handler fixed-clock tests (all
    five terminal paths + SUCCESS→PROCESSED + EMPTY_RESULT→ERROR), `FindImportRunStatusQueryHandlerTest`
    counter-to-DTO case, `ImportRunStatusPolicyTest`, `NavigatorImportExecutionLifecycleTest` (plan
    cases a–f), navigator lifecycle assertions in the FCTT/RFETM placeholder tests and a new BCNESA
    no-team placeholder case, and process-service counter/warning mapping.
  - Validation: full `mvn test` — only the recorded pre-existing failures remain (4
    `InitialUserProvisioningServiceTest`, 8 import processor tests). No schema change, so
    `rfetm-datamodel.md` was untouched (`last_processed_date` is documented as nullable with no
    "unused" note).
- 2026-09-28: All four acceptance criteria verified against the delivered behavior and checked;
  closed as `done` on explicit user request.
