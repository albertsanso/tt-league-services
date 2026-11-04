# Build Plan
Approach: a run is split into **run units**. Each unit owns one scope (one `ScopeFilter`, or the whole season) and
executes its own `INGEST → FETCH_PACKAGE → IMPORT` chain, so units fail, time out, report progress and are retried
independently. Units of one run execute **sequentially** (ingest accepts one active run per source). The run status
is derived from its units. Existing runs are backfilled as single-unit runs so they stay readable.

## 1. Core: unit identity and planning (`tt-league-pipeline-orchestrator-core`, `run` package)

1. Add `run/UnitKey` with the canonical-string + SHA-256 logic currently in `PollUnit.scopeKey()`; make
   `PollUnit.scopeKey()` delegate to it so existing `poll_schedule.scope_key` values stay identical (pin with a
   test that compares both for a fixed set of units). The key excludes match days. A full-season unit uses the
   constant key `season`; backfilled multi-filter legacy units use `legacy`.
2. Add `run/UnitStatus`: `PENDING, RUNNING_INGEST, PACKED, IMPORTING, NO_CHANGES, SUCCEEDED, PARTIAL, FAILED,
   SKIPPED`, with transitions mirroring today's `RunStatus` plus `PENDING→PACKED` (replay), `PENDING→SKIPPED`
   and `PENDING→FAILED`; `isTerminal()`/`isActive()` as in `RunStatus`.
3. Add `run/UnitProgress` record: `StepKind step, String stage` (ingest stage name, nullable),
   `long itemsProcessed, Long itemsTotal` (nullable when unknown), `String currentItem` (≤256, nullable),
   `Instant updatedAt`. Validation: non-negative, `itemsProcessed ≤ itemsTotal` when total is known.
4. Add `run/RunUnit` immutable aggregate (same style as `PipelineRun`: private constructor, `plan(...)`,
   `restore(...)`, transition methods returning new instances, `validateInvariants()`): `id, runId, ordinal,
   unitKey (≤64), label (≤256), scope (RunScope: zero or one filter; more only for legacy), status, startedAt,
   finishedAt, ingestRunId, importJobId, error, progress, version`. Methods: `startIngest`, `restartIngest`,
   `noChanges`, `packed`, `startReplay`, `startImport`, `succeed`, `partial`, `fail(RunError)`,
   `skip(RunError)`, `withProgress(UnitProgress)` (not a transition; allowed only while active; cleared on
   terminal transitions). Invariants copied from today's `PipelineRun` (importJobId required for
   IMPORTING/SUCCEEDED/PARTIAL, error exactly for FAILED/SKIPPED, finishedAt exactly for terminal, ordering of
   timestamps).
5. Add `run/UnitPlanner.plan(UUID runId, RunScope scope)` (pure): full season → one unit `season` / label
   "Full season"; otherwise one unit per distinct `PollUnit` identity, merging match days of filters that share
   an identity (same rule as `ScopeBuilder`), ordinal in first-seen order, label built from the filter fields
   (`territory category gender group phase · J3,J4`). `UnitPlanner.replan(...)` copies units of an original run
   for replay (see step 13).
6. Add `run/RunOutcomeRules.derive(List<RunUnit>)` (pure, the only place that derives run status from units):
   all `NO_CHANGES` → `NO_CHANGES`; every unit in {`SUCCEEDED`,`NO_CHANGES`} with at least one `SUCCEEDED` →
   `SUCCEEDED`; any `PARTIAL`, or a mix of failed/skipped and successful/no-change units → `PARTIAL`; all
   `FAILED`/`SKIPPED` → `FAILED` with error = code of the first failed unit and message
   "`n` of `m` units failed: <codes>". Rejects a list with an active unit.
7. Change `RunStatus` to `QUEUED, RUNNING, NO_CHANGES, SUCCEEDED, PARTIAL, FAILED`
   (`QUEUED→RUNNING|FAILED`, `RUNNING→NO_CHANGES|SUCCEEDED|PARTIAL|FAILED`). The removed values become unit
   statuses only; the migration (step 21) rewrites active legacy rows.
8. Change `PipelineRun`: replace `startIngest/restartIngest/noChanges/packed/startReplay/startImport/succeed/
   partial` with `start(Instant)` and `finish(RunStatus derived, RunError errorOrNull, Instant)`; drop the
   `ingestRunId`/`importJobId` fields and their invariants (they move to `RunUnit`); add `retryOfUnitId` with the
   invariant `(trigger == UNIT_RETRY) == (retryOfUnitId != null)` and `retryOfRunId` required for `RETRY` and
   `UNIT_RETRY`. Add `RunTrigger.UNIT_RETRY`.
9. Ports: add `run/port/RunUnitRepository` (`addAll(List<RunUnit>)` — one atomic write, `update(RunUnit)` with
   optimistic version → `StaleRunException`, `findByRunId(UUID)` ordered by ordinal, `findById(UUID)`,
   `findRetriesOf(UUID unitId)`). `PipelineStep` gains required `unitId` (attempt numbering per unit+kind);
   `RunArtifact` and `ImportReport` gain `unitId`; `ImportReportRepository.findByUnitId`. `RunQuery` gains an
   optional `unitKey` filter.
10. `RunObserver` gains `default void unitChanged(RunUnit unit) {}`; `CompositeRunObserver` forwards it with
    the same catch-and-log rule.

## 2. Core: execution

11. Split `RunExecutor`:
    - `RunExecutor.execute(runId)` stays the only entry point. `drive`: if the run is `QUEUED`, plan units when
      none exist (`UnitPlanner`, or replay copy for `RETRY` runs) via `units.addAll`, then `run.start`. Then
      execute the units in ordinal order, skipping terminal ones (resume continues at the first active unit).
      After each unit, if it failed with a run-aborting code (`INGEST_UNAVAILABLE`, `INGEST_BUSY`,
      `PLATFORM_UNAVAILABLE`, `ARTIFACT_STORE_FAILED`, `INTERNAL_ERROR`), skip every remaining `PENDING` unit with
      new `FailureCode.UNIT_SKIPPED` ("skipped after <code> on unit <label>"). Finish the run with
      `RunOutcomeRules.derive`. Keep the abort list in one constant (`UnitExecutionRules.abortsRemaining`) with
      a test per code.
    - New package-private `UnitExecutor` holds today's ingest/replay/fetch/import phase code, operating on a
      `RunUnit` instead of the run: steps carry `unitId`, deadlines are per unit and kind (first attempt of that
      unit), `IngestRunRequest.forUnit(run, unit)` sends the unit scope (`SNAPSHOT` only for the `season` unit)
      with `correlationId` = run id, the import submit file name is `<runId>-<ordinal>.zip` and `clientRunId`
      stays the run id, and the import report is stored per unit. Retry rules, failure codes and messages are
      unchanged.
    - Storage key per unit: `<source>/<season>/<runId>/<ordinal>-<unitKey first 12>/ingest-<ingestRunId>.zip`.
    - Progress: while polling ingest, when `IngestRunState.progress()` differs from the unit's stored progress,
      `units.update(unit.withProgress(...))` and `observer.unitChanged`. FETCH_PACKAGE and IMPORT set a
      progress with `step` and no item counts (the platform job exposes no item progress).
    - `failUnexpected` fails running steps, the active unit (`INTERNAL_ERROR`), skips pending units and fails
      the run.
12. `IngestRunState` gains nullable `IngestProgress progress` (`stage, itemsProcessed, itemsTotal, currentItem`);
    keep the existing constructors (null progress). `RunRecovery` unchanged in behaviour (re-executes active runs).

## 3. Core: replay and unit retry

13. Replay (`ReplayRules`, `ReplayRun`, `UnitExecutor.replayPhase`): a replay copies every unit of the original
    that has an unpurged `ZIP` row (same key, label, scope, new ids); units without a package are not copied.
    `NO_PACKAGE` when no unit has a ZIP row; `ARTIFACT_PURGED` when every ZIP row is purged. Each copied unit
    goes `PENDING→PACKED` sharing the original storage key (file never copied).
14. Add `trigger/UnitRetryRules.check(run, unit, activeRun)` (the only place deciding eligibility):
    `RUN_ACTIVE` (the run itself is not terminal, or the source has an active run), `UNIT_NOT_RETRYABLE`
    (unit not `FAILED`/`SKIPPED`; `STEP_TIMEOUT` failures are `FAILED` and therefore retryable). Add
    `trigger/RetryUnit` (only creator of `UNIT_RETRY` runs): launches through `RunLauncher` a run with
    `scope = unit.scope`, `force = original.force`, `retryOfRunId`, `retryOfUnitId`, never queued
    (`Rejected` like `ReplayRun`). The retry run executes the full chain (re-ingest). The original run and unit
    stay unchanged (history is immutable); the API links them (`retriedBy`).

## 4. Core: statistics and alerts

15. Statistics: add `statistics/UnitFacts(runId, source, unitKey, label, status, startedAt, finishedAt)` read via
    `StatisticsReadRepository.unitFacts(DateRange, Optional<PipelineSource>, Optional<String> unitKey)`;
    `StepFacts` gains `unitKey`. `StatisticsRules.unitOutcomes` (counts per unit key and status, average
    duration) and the existing run-outcome and step-average figures accept an optional unit-key filter.
    `daily_stats` stays per source (no change to `DailyStatsAggregator`).
16. Alerts: add `AlertKind.UNIT_FAILURES`: holds when the same unit key failed (`FAILED`, not `SKIPPED`) in its
    two newest terminal occurrences; reference `<source>:<unitKey>`; texts in `AlertTexts` use the unit label
    and `RunError.code`. `AlertFacts` gains `newestTerminalUnits` (up to two per source and unit key);
    `AlertSettings` gains `unitKeys` (empty = all units) so operators can limit unit alerts to chosen units.
    `RUN_FAILURES` is unchanged (now also fires for a run that is `FAILED` because all units failed).
17. Fixtures in the core `test-jar`: `InMemoryRunUnitRepository`; update `InMemoryPipelineStepRepository`,
    `InMemoryRunArtifactRepository`, `InMemoryImportReportRepository`, `InMemoryStatisticsReadRepository`,
    `ScriptedIngestGateway` (scripted progress per poll, per-scope answers), `RecordingObserver` (units),
    `ExecutorHarness`.

## 5. Ingest progress (`tt-league-ingest`)

18. `ingest_common/run.py`: add `stage_total(stage, total)` to `ProgressListener` and `NoOpListener`; call it in
    the pipelines where the item list is known before processing (download listing, parse inputs).
19. `ingest_rest/app.py`: `RunRecord` keeps `progress` (`stage`, `itemsProcessed`, `itemsTotal`, `currentItem`);
    `_RecordListener` resets it on `stage_started`, sets the total on `stage_total`, increments and records the
    item on `item_processed`; `to_dict` adds `"progress"` (null before the first stage). Thread-safety: the
    listener runs on the worker thread, reads take a snapshot.
20. Tests under `tt-league-ingest/tests` for the listener and the REST payload; update the ingest REST README
    section on `GET /api/v1/ingest/runs/{id}`.

## 6. Runtime: persistence (`tt-league-pipeline-orchestrator-runtime`)

21. Flyway `V10__run_units.sql`:
    - `pipeline.pipeline_unit` (`id` PK, `run_id` FK, `ordinal`, `unit_key varchar(64)`, `label varchar(256)`,
      `scope jsonb`, `status` with CHECK on `UnitStatus`, `started_at`, `finished_at`, `ingest_run_id`,
      `import_job_id`, `error_code`, `error_message`, `progress_step`, `progress_stage`, `progress_items`,
      `progress_total`, `progress_current varchar(256)`, `progress_updated_at`, `version`), `UNIQUE (run_id,
      ordinal)`, index `(unit_key, finished_at DESC)`, CHECKs mirroring the aggregate invariants.
    - Backfill one unit (ordinal 0) per existing run: key `season` (empty scope) else `legacy`, label
      "Full season"/"Legacy scope", scope/ingest_run_id/import_job_id/error/timestamps copied, status mapped
      (`QUEUED→PENDING`, intermediate and terminal statuses 1:1).
    - `pipeline_run`: rewrite `RUNNING_INGEST/PACKED/IMPORTING` to `RUNNING`; replace the status CHECK; recreate
      `ux_pipeline_run_active_source` over `('QUEUED','RUNNING')`; add `retry_of_unit_id uuid NULL REFERENCES
      pipeline.pipeline_unit(id)`; extend the trigger CHECK with `UNIT_RETRY` and replace the retry CHECK.
      `ingest_run_id`/`import_job_id` stay as nullable legacy columns, no longer written (documented).
    - `pipeline_step.unit_id` and `run_artifact.unit_id` (FK, backfilled, then `NOT NULL`); replace
      `UNIQUE (run_id, kind, attempt)` with `UNIQUE (unit_id, kind, attempt)`.
    - `import_report`: add `unit_id` (backfilled, `NOT NULL`, `UNIQUE`), move the PK from `run_id` to `unit_id`,
      keep `run_id` FK + index.
    - `alert`: extend the kind CHECK with `UNIT_FAILURES`.
22. JPA: `RunUnitEntity`, `RunUnitJpaRepository`, `JpaRunUnitRepository` (version check → `StaleRunException`,
    `addAll` in one transaction); update `PipelineRunEntity` (drop the two mapped legacy fields, add
    `retryOfUnitId`), `PipelineStepEntity`, `RunArtifactEntity`, import report entity, mappers,
    `JpaStatisticsReadRepository` (unit facts, step unit key, corrections summed per run across unit reports),
    `JpaPipelineRunRepository` (unit-key filter via `EXISTS` on `pipeline_unit`).
23. Update `docs/pipeline-datamodel.md`: new `pipeline_unit` section, changed columns/constraints, run and unit
    status transitions, "Written by the run executor" table, legacy columns, migration history entry.

## 7. Runtime: gateways, wiring, API and events

24. `IngestServiceJobRunner`: map the optional `progress` object to `IngestProgress` (absent → null; malformed →
    protocol error); single-scope requests for unit runs.
25. Wiring: `RunExecutionConfiguration` passes `RunUnitRepository` to `RunExecutor`, `RetryUnit` bean;
    `AlertSettings.unitKeys` from `pipeline.alerts.unit-failures.unit-keys` (empty default); `RunMetricsObserver`
    adds `pipeline.unit.finished` counter tagged `source`, `status` (never the unit key — cardinality);
    `LoggingRunObserver` logs unit transitions with `runId` and `unitKey` in the MDC (`RunLogContext`).
26. DTOs (`RunDtoMapper`):
    - `RunUnitDto(id, ordinal, unitKey, label, filters, status, startedAt, finishedAt, durationMs, error,
      ingestRunId, importJobId, progress, counters)`.
    - `RunSummaryDto` gains `retryOfUnitId` and `units` (summary form without counters; included in lists and
      events); `ingestRunId`/`importJobId` are filled from the single unit of a one-unit run, else null.
    - `RunDetailDto` gains per-unit detail: steps (`StepDto.unitId`), artifacts (`ArtifactDto.unitId`),
      `ImportReportDto` per unit, `storageFolder` (relative artifact folder of the unit), `packageUrl` (null when
      no unpurged ZIP), `retry {eligible, reason}` from `UnitRetryRules`, `retriedBy [{runId, status}]`. The
      run-level `importReport` becomes the sum over units (same field names).
27. Endpoints in `RunsController` (same auth/permission as replay):
    - `POST /api/pipeline/runs/{id}/units/{unitId}/retry` → `202 {runId}`, `409 RUN_ACTIVE`,
      `422 UNIT_NOT_RETRYABLE`, `404` unknown run/unit or unit of another run.
    - `GET /api/pipeline/runs/{id}/units/{unitId}/package` → streams the unit ZIP (`404` none, `410` purged).
    - `GET /api/pipeline/runs?unitKey=...` filter.
    - `GET /api/pipeline/statistics/units?from&to&source` (unit outcomes) and `unitKey` on the existing
      `runs` statistics endpoint.
28. `RunEventBroadcaster`: new SSE event `unit` with `RunUnitDto` (summary + progress) on `unitChanged`; `run`
    events carry the units summary. Progress events go through the existing bounded queue (drop rule unchanged).

## 8. Frontend (`tt-league-pipeline-orchestrator-frontend`)

29. `api/types.ts` + `api/runs.ts`: `RunStatus` (`RUNNING` replaces the three intermediate statuses), `UnitStatus`,
    `RunUnit`, `UnitProgress`, retry/package endpoints, `unitKey` filter; `runs/runStatus.ts` labels/colours
    for both enums.
30. `RunsTable`: collapsible row per run listing units (status chip, label, duration, error code, a compact
    progress bar for the active unit, "n/m units" summary in the collapsed row).
31. `RunDetailPage`: "Units" section with one MUI `Accordion` per unit (failed units expanded by default):
    status, timings, counters, error, steps of the unit, determinate `LinearProgress` when `itemsTotal` is known
    (indeterminate otherwise) with stage and current item, storage folder with copy button, "Download package"
    link, "Retry unit" button gated by `Can` and `retry.eligible` (reason as tooltip) with a confirm dialog
    (like `ReplayRunDialog`), links to retry runs and to the original run/unit. A "JSON" tab shows the run
    detail payload pretty-printed with a copy button.
32. `RunEventsProvider`/`useRunDetail`/`useRunList`: apply `unit` events (replace by unit id, ignore older
    `progress.updatedAt`).
33. Statistics: unit filter in `StatisticsFilterBar` fed by `/statistics/units`; `RunOutcomesPanel` honours it
    and a new "Unit outcomes" panel lists failure rates per unit. `RunFilterBar` gains a unit filter.
34. Vitest tests for the status mapping, unit row/accordion rendering, progress bar, retry dialog
    (eligible/ineligible), event merge and filters; update fixtures that use removed run statuses.

## 9. Tests (JUnit 5, alongside each module)

35. Core: `UnitKeyTest` (parity with `PollUnit`), `UnitPlannerTest` (season, one filter, merged identities,
    labels, ordering), `RunUnitTest` (transitions/invariants), `RunOutcomeRulesTest` (all-succeeded,
    all-no-changes, partial mixes, all failed/skipped, active rejected), `RunStatusTest`/`PipelineRunTest`
    updated, `RunExecutorTest` (multi-unit success, one unit failing while others succeed → `PARTIAL`, unit
    timeout, run-aborting code skips the rest, progress updates written only on change), `RunExecutorResumeTest`
    (resume mid-unit and between units, legacy backfilled run), replay tests (per-unit copy, `NO_PACKAGE`,
    partial purge), `UnitRetryRulesTest`, `RetryUnitTest`, `StatisticsRulesTest` (unit filter, unit outcomes),
    `AlertRulesTest` (`UNIT_FAILURES`, unit-key setting, skipped units ignored), `CoreDependencyRulesTest` green.
36. Runtime: migration test on a V9 database with legacy runs in every status (backfill, rewritten active
    statuses, constraints), `JpaRunUnitRepositoryTest`, updated `ActiveRunConstraintTest`,
    `JpaPipelineRunRepositoryTest` (unit-key filter), `JpaStatisticsReadRepository` tests, `RunExecutorHttpTest`
    (multi-unit run against stub ingest/platform, progress mapping), `RunsController` tests for retry and
    package endpoints and DTO shape, `RunEventBroadcaster` test for `unit` events, `IngestServiceJobRunner`
    progress parsing (present, absent, malformed).
37. Ingest: pytest for progress tracking and payload.

## 10. Documentation and validation

38. Update `tt-league-pipeline-orchestrator-runtime/README.md` (units, statuses, retry/package endpoints, SSE
    `unit` event, `pipeline.alerts.unit-failures.unit-keys`), the frontend README if it documents pages,
    `tt-league-ingest` REST README (progress), `docs/pipeline-datamodel.md` (step 23), and
    `tt-league-pipeline-orchestrator-core/AGENTS.md` (unit rules: `UnitPlanner`, `RunOutcomeRules`,
    `UnitRetryRules` as the only deciders; new fixtures).
39. Validate: `mvn -pl tt-league-pipeline-orchestrator-core -am test`,
    `mvn -pl tt-league-pipeline-orchestrator-runtime -am test`,
    `mvn -pl tt-league-pipeline-orchestrator-frontend -am test`, `uv run pytest` in `tt-league-ingest`, then the
    full `mvn test`.

Implementation order: 1 → 2 → 3 → 4 (core, with fixtures) · 5 (ingest, independent) · 6 → 7 (runtime) · 8
(frontend) · 9/10 throughout, full build last.

# Implementation Guidelines

- Follow the core, runtime and frontend `AGENTS.md`. The core stays free of Spring, JPA and HTTP clients;
  `UnitPlanner`, `RunOutcomeRules`, `UnitExecutionRules`, `UnitRetryRules` are pure and are the only places that
  decide unit identity, run status, abort behaviour and retry eligibility. The runtime and the UI show their answers
  and never re-derive them.
- Every unit change goes through `RunUnitRepository.update` and notifies `RunObserver.unitChanged`; every step change
  still goes through `steps.save`. No broad catches outside `CompositeRunObserver`.
- Unit keys must stay byte-identical to `PollUnit.scopeKey()` so `poll_schedule`, statistics and alerts refer to the
  same unit. Never include match days in the key.
- History is immutable: a unit retry creates a new `UNIT_RETRY` run; it never reopens a terminal run or unit.
- Retry rules, failure codes and messages of the existing steps are unchanged; the only new codes are
  `UNIT_SKIPPED` (run error/unit error) and the API answers `UNIT_NOT_RETRYABLE`.
- Progress rows are written only when the reported progress changed; never write on every poll.
- Unit keys are never metric tags (cardinality); they may appear in logs/MDC and in API payloads.
- Existing runs must stay readable: the migration backfills one unit per run; do not drop the legacy
  `pipeline_run.ingest_run_id`/`import_job_id` columns in this feature.
- Every schema change ships in `V10__run_units.sql` with `docs/pipeline-datamodel.md` updated in the same change.

Out of scope:

- Splitting a full-season (snapshot) run into groups: ingest only packages scoped runs in delta mode, and the
  snapshot is what detects unpublished/shrunk content. A full-season run is one `season` unit.
- Parallel execution of units (ingest allows one active run per source).
- Item-level progress for the platform import job (the platform import-job API exposes none); IMPORT progress is
  step-level only.
- Exposing ingest's raw download folder (actas-json working directory); the unit shows the orchestrator's artifact
  storage folder and a ZIP download.
- Changing adaptive polling decisions to use unit outcomes (keys are aligned so a later feature can).
- Dropping the legacy run columns.

# Notes

- 2026-10-06 — Plan created. Decisions:
  - **D1 Fan-out execution.** Each unit runs its own ingest + import chain rather than one shared ingest run reporting
    a per-scope breakdown: only fan-out gives true per-unit failure isolation, timeouts and retry. Cost: one ingest
    run and one import job per unit, executed sequentially, so a multi-group run takes longer (one ingest start-up per
    unit) and the platform receives several smaller delta ZIPs (deduplicated by content).
  - **D2 Run status set reduced** to `QUEUED, RUNNING, NO_CHANGES, SUCCEEDED, PARTIAL, FAILED`; the old intermediate
    statuses move to `UnitStatus`. Only the executor, `PipelineRun`, migration V1 constraints, tests and frontend
    status mapping reference them today. `PARTIAL` now also means "some units failed".
  - **D3 Unit retry = new `UNIT_RETRY` run** linked by `retry_of_run_id` + `retry_of_unit_id` (same pattern as
    replay), so terminal history, statistics and alerts are never rewritten. The original run keeps its derived
    status; the UI links the retry and its outcome.
  - **D4 Run-aborting codes** (`INGEST_UNAVAILABLE`, `INGEST_BUSY`, `PLATFORM_UNAVAILABLE`, `ARTIFACT_STORE_FAILED`,
    `INTERNAL_ERROR`) skip the remaining units instead of hammering an unavailable service once per unit.
  - **D5 Alerts by unit** through a new `UNIT_FAILURES` kind (same unit failed in its two newest occurrences) plus
    the `pipeline.alerts.unit-failures.unit-keys` setting; there is no alerts UI/API to filter today.
- Open questions (confirm before moving to `ready`):
  1. Is the longer wall-clock time of sequential per-unit ingest acceptable for large GROUP runs (e.g. adaptive
     polling over many due units), or should units be batched into fewer ingest runs?
  2. Should a successful unit retry be reflected on the original run (e.g. a "resolved" badge) beyond the link?
  3. Is "download folder" satisfied by the artifact storage folder + ZIP download, or is ingest's working folder
     wanted (would need a new ingest endpoint)?
  4. Effort is large; consider splitting into (a) core/runtime units + migration + API, (b) progress reporting
     incl. ingest, (c) UI, statistics and alerts.

- 2026-10-06 — Status set to `ready` on the user's explicit request. The open questions above were not answered; the
  plan's defaults stand (sequential per-unit ingest, artifact folder + ZIP download, single feature, no "resolved"
  badge on the original run).
- Implementation finalized; see the implementation notes below for validation results and deviations.
- 2026-10-06 — Implementation notes and validation:
  - **Deviations from the plan.** The unit-failure alert setting is `tt.pipeline.notifications.unit-failures.unit-keys`
    (env `PIPELINE_ALERTS_UNIT_KEYS`), next to the other alert settings, not `pipeline.alerts...`. A terminal `unit` event
    carries the unit's import `counters`; `run` events and list rows carry the units without counters. The run detail keeps
    the run-level Steps/Artifacts/Import report sections (summed over the units) next to the per-unit accordions. The run-list
    unit filter is a free-text unit key (the unit key is shown with a copy button in the unit accordion). The activity log does
    not list `unit` events.
  - **Acceptance criteria verified** (left unchecked until closure): per-unit status/timings/counters/errors in the runs API
    and events (core `RunExecutorUnitsTest`, runtime `RunDtoMapperTest`, `RunsApiWebTest`, `RunEventBroadcasterTest`);
    progress (ingest `test_progress.py`/`test_rest.py`, `IngestServiceJobRunnerTest`, `JpaRunUnitRepositoryTest`); unit retry
    and derived status (`RetryUnitTest`, `UnitRetryRulesTest`, `RunOutcomeRulesTest`, `RunsApiWebTest`); UI, statistics and
    alerts by unit (Vitest, `StatisticsApiWebTest`, `UnitStatisticsTest`, `AlertUnitFailuresTest`); migration `V10` with
    backfill and constraints (`RunUnitsMigrationTest`, `RunUnitsBackfillMigrationTest`) and `pipeline-datamodel.md`; docs in the
    runtime, ingest and frontend READMEs and the core, runtime and frontend `AGENTS.md`.
  - **Validation.** Core 665+ tests, ingest 365 pytest, frontend 463 Vitest plus lint and typecheck: green. Runtime: every
    new and changed test passes, including the Testcontainers ones. The persistence tests need Docker; under the local podman
    machine a long run intermittently loses the Docker connection, so they were run in smaller groups and individually.
    Failures that remain are **pre-existing** (identical on a clean `HEAD` worktree): `PipelineOrchestratorPropertiesTest.
    failsWhenTheStatisticsZoneIsMissingBlankOrInvalid`, `TrackerRecomputeIntegrationTest` (no `HttpSecurity` bean in a
    non-web context), `JpaPollPolicyRepositoryTest`, `JpaMatchDayRepositoryTest` (null precedence), `MatchDayTrackerMigrationTest`
    and the "unknown/second row is rejected" tests of `JpaAlertRepositoryTest`, `JpaImportReportRepositoryTest` and
    `JpaPipelineRunRepositoryTest`. `tt-data-league-import` also fails `BcnesaImportProcessorsTest` (missing fixture
    `acta_bcnesa_2026_published.json`), unrelated to this feature, so the full `mvn test` is not green.
