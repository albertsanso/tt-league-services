# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>`, `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>` and `tt-league-pipeline-orchestrator-frontend/src` as `<fe>`. Test sources mirror them. Core test fixtures live
in `<core-test>/execution/testing`, the published `test-jar`.

The plan has five parts:
- Steps 1-7: the orchestrator core (replay, idempotency record, retention).
- Steps 8-14: the orchestrator runtime (migration, persistence, API, cleanup job, configuration).
- Steps 15-18: the orchestrator frontend.
- Steps 19-20: documentation and validation.

No platform, import or `tt-league-ingest` module changes. No new dependency, no root or parent POM change.

**Decision (2026-10-05, user).** Replay is orchestrator-only and always respects the platform's content
deduplication. A replay re-imports only when the platform has no active, `SUCCEEDED` or `PARTIAL` job for the same
`contentSha256`, which covers the "fix, then replay" case for runs whose import `FAILED`, was rejected or never ran. For
an original whose import ended `SUCCEEDED`/`PARTIAL` the replay receives the existing job, records that it was reused,
and nothing is re-imported. A "force re-import" platform option is a follow-up (see `# Notes`).

**Contracts used (read from the code on 2026-10-05).**

*Orchestrator core*
- `RunTrigger.RETRY` and `PipelineRun.retryOfRunId` already exist. The invariant
  `(trigger == RETRY) == (retryOfRunId != null)` is enforced by `PipelineRun` and by
  `CHECK ((trigger = 'RETRY') = (retry_of_run_id IS NOT NULL))` in `V1`. Nothing creates a `RETRY` run today:
  `TriggerRun.Command` rejects it, and `RunLauncher.LaunchRequest` already carries `retryOfRunId`.
- `RunStatus`: `QUEUED -> RUNNING_INGEST | FAILED`, `RUNNING_INGEST -> NO_CHANGES | PACKED | FAILED`,
  `PACKED -> IMPORTING | FAILED`, `IMPORTING -> SUCCEEDED | PARTIAL | FAILED`. `PACKED` requires `startedAt` and forbids
  `importJobId`; nothing requires `ingestRunId`.
- `RunExecutor.drive` sends `QUEUED`/`RUNNING_INGEST` to `ingestPhase`, `PACKED` to `packedPhase`. `packedPhase` looks
  for the run's own `ArtifactKind.ZIP` row: without one it fetches the package from ingest, with one it goes to
  `importFromPacked`, which submits `artifacts.content(zip.storageKey())` through `ImportGateway.submit(fileName,
  content, run.id())`.
- `ImportSubmission(importJobId, status, created)` already carries the platform's `created` flag (`202` = new job,
  `200` = existing job for the same content); the executor ignores it today.
- `RunArtifact(id, runId, kind, storageKey, sha256, sizeBytes, createdAt)`; the package key is
  `<source lower-case>/<season>/<runId>/ingest-<ingestRunId>.zip`. Only `ZIP` artifacts are written today; `MANIFEST`,
  `RAW` and `JSON` exist in the enum and schema but have no writer.
- `ArtifactStore` has `store`, `content`, `exists` and an idempotent `delete`.

*Orchestrator runtime and frontend*
- `SecurityConfiguration` protects `POST /api/pipeline/runs` (exact path) with `matches:write`; everything else under
  `/api/pipeline/**` falls to `anyRequest().authenticated()`, so a new mutation path needs its own matcher.
- `RunDetailDto` = summary + steps + artifacts + import report + issues. `ArtifactDto(kind, sha256, sizeBytes,
  createdAt)` hides the storage key. `RunSummaryDto` already exposes `trigger` and `retryOfRunId`, and
  `RunDetailPage` already links "Retry of <id>".
- Scheduled jobs follow `DailyStatsSchedule`: a `SmartLifecycle` with a private `ThreadPoolTaskScheduler` (not a bean)
  and a ShedLock lock taken through `LockingTaskExecutor`.

*Platform (unchanged)*
- `ImportJobService.submit` returns the existing job (`created=false`, HTTP 200) when the manifest has `contentSha256`
  and a job of the same source and hash is active or ended `SUCCEEDED`/`PARTIAL`. A `FAILED` job is not reused, so a
  new job is created. The published-acta shrink check runs after deduplication.

## Orchestrator core

1. **Replay transition (`<core>/run`).**
   - `RunStatus`: add `PACKED` to the successors of `QUEUED`.
   - `PipelineRun.startReplay(Instant at)`: `QUEUED -> PACKED`, sets `startedAt = at`, and throws
     `IllegalRunTransitionException` unless `trigger == RETRY`. `ingestRunId` stays null.
   - `PipelineRunTest`: covers `startReplay` for a `RETRY` run, its rejection for `MANUAL`/`SCHEDULED` runs and for
     non-`QUEUED` statuses, and that `QUEUED -> PACKED` is otherwise unreachable through `packed(...)`, which still
     requires `RUNNING_INGEST`. Add a `RunStatusTest` case for the new successor.

2. **Purged artifacts (`<core>/run`).**
   - `RunArtifact` gains a nullable `Instant purgedAt` component (`isPurged()`), validated as not before `createdAt`.
     Add a secondary constructor without it for the existing unpurged call sites.
   - `RunArtifactRepository` gains:
     - `List<RunArtifact> findByStorageKey(String storageKey)`
     - `int markPurged(String storageKey, Instant at)`, which marks every row of the key that is not yet purged and
       returns the count
   - Update `InMemoryRunArtifactRepository` in the `test-jar`.

3. **Recording a reused import job (`<core>/run`, `<core>/execution`).**
   - `PipelineStep` gains a nullable `Boolean importJobReused`. It is allowed only on `IMPORT` steps and is set
     together with the external reference by a new `withImportJob(UUID importJobId, boolean reused)`, which delegates
     to the same once-only rule as `withExternalRef`.
   - `restore` takes the new component; `succeed`/`fail` keep it.
   - `RunExecutor.importAttempt` calls `step.withImportJob(submission.importJobId(), !submission.created())` instead of
     `withExternalRef`. This applies to every run, not only replays, so normal runs also record a deduplicated
     submission.
   - `ScriptedImportGateway` gains a way to answer `created=false` with an already finished job.

4. **Replay rules (`<core>/trigger/ReplayRules.java`, pure, static).**
   - `ReplayEligibility check(PipelineRun original, List<RunArtifact> artifacts)` returns `allowed` or one code:
     - `RUN_ACTIVE`: the original is not terminal
     - `NO_PACKAGE`: there is no `ZIP` row, for example a `NO_CHANGES` run, or a run that failed before
       `FETCH_PACKAGE` stored the package
     - `ARTIFACT_PURGED`: the `ZIP` row is purged
   - A replay run can itself be replayed: it carries its own copy of the `ZIP` row (step 6).
   - `ReplayEligibility(boolean allowed, String code)` is the one value the runtime exposes (step 12). The API and the
     UI never re-derive it.

5. **`ReplayRun` (`<core>/trigger/ReplayRun.java`), the only creator of `RETRY` runs.**
   - `Outcome replay(UUID originalRunId, String requestedBy)` returns one of these sealed outcomes:
     - `Created(run)`
     - `NotFound`
     - `NotReplayable(code, message)`: the codes of step 4, plus `ARTIFACT_PURGED` when the row is unpurged but
       `ArtifactStore.exists` is false
     - `Rejected(ACTIVE_RUN, message, activeRunId)`
   - It launches through `RunLauncher.launch(new LaunchRequest(original.source(), original.season(),
     original.scope(), original.force(), RETRY, requestedBy, original.id()))`.
   - `ActiveRunConflictException` maps to `Rejected`. Replays are never queued as pending triggers: `ConflictMode`
     does not apply.
   - `TriggerRun` stays the only creator of `MANUAL`/`SCHEDULED` runs and is not changed.
   - `ReplayRunTest` covers each outcome, using `InMemoryPipelineRunRepository`, `InMemoryRunArtifactRepository`,
     `InMemoryArtifactStore` and `RecordingDispatcher`.

6. **Replay path in `RunExecutor`.**
   - `drive`: `QUEUED` with `trigger == RETRY` goes to a new `replayPhase`. Every other status keeps its current
     branch, so a replay continues through the unchanged `packedPhase -> importFromPacked -> importingPhase`, with its
     retries, timeouts and resume behaviour.
   - `replayPhase(run)`:
     1. Read the original's `ZIP` row. When it is missing or purged, or the file does not exist, fail the run with the
        new `FailureCode.ARTIFACT_PURGED` (final, not retried). It is never `PACKAGE_GONE`, which means ingest lost
        the package.
     2. Re-hash the stored content and compare it with the row's `sha256`. A mismatch fails the run with
        `PACKAGE_CHECKSUM_MISMATCH`. An `ArtifactStoreException` fails it with `ARTIFACT_STORE_FAILED`.
     3. If the replay run has no `ZIP` row yet, add a copy: new id, the replay `runId`, the same `storageKey`,
        `sha256` and `sizeBytes`, and `createdAt = now`. The file is shared, never copied.
     4. `updateRun(run.startReplay(now))`.
   - No `INGEST` or `FETCH_PACKAGE` step is created, and `IngestGateway` is never called.
   - Resume rule: a crash after step 3 leaves a `QUEUED` `RETRY` run with its own `ZIP` row. The next `execute` skips
     the copy and moves on, so `RunRecovery` needs no change.
   - New `RunExecutorReplayTest`:
     - the happy path to `SUCCEEDED` with zero ingest calls
     - `created=false` with a finished `SUCCEEDED` job: the step records `importJobReused = true`, the run ends
       `SUCCEEDED`, and a report is stored for the replay run
     - a fresh job for an original whose import `FAILED`
     - `PARTIAL` and `FAILED` import results
     - a missing file (`ARTIFACT_PURGED`) and a checksum mismatch
     - resume after a crash between the row copy and `startReplay`, and resume while `IMPORTING`
   - Extend `RunExecutorTest` to assert `importJobReused = false` on a normal `202` submission.

7. **Artifact retention (`<core>/retention`, new package).**
   - `RetentionRule`, sealed:
     - `MaxAge(Duration)`, positive
     - `Seasons(int)`, from 1 to 10: keep the artifacts of the newest N seasons for which the source has runs
   - `RetentionPolicy(Map<ArtifactKind, RetentionRule>)` requires a rule for every `ArtifactKind`.
   - `RetentionRules.expired(List<RetainedArtifact> artifacts, Map<PipelineSource, List<String>> seasonsBySource,
     RetentionPolicy policy, Instant now)` is pure and static, with no I/O and no clock. It returns the storage keys to
     purge. The rules:
     - Rows are grouped by `storageKey`, since replay rows share a key. The age is that of the oldest row (the
       original), and the source and season are the original run's.
     - A key is never expired while any run that references it is active.
     - Seasons are ordered by the `YYYY-YYYY` value.
   - `RetainedArtifact(RunArtifact artifact, PipelineSource source, String season, boolean runActive)` is a read value.
   - Port `ArtifactRetentionRepository`:
     - `List<RetainedArtifact> findUnpurged()`
     - `Map<PipelineSource, List<String>> seasonsBySource()`
   - `ArtifactCleanup.run()` returns `CleanupOutcome(purgedKeys, purgedBytes, failedKeys)`. For each expired key it
     calls `ArtifactStore.delete(key)`, then `RunArtifactRepository.markPurged(key, now)`. The file is deleted first,
     so a crash in between leaves an unpurged row whose file is gone. The next pass deletes again (idempotent) and
     marks it, and replay already treats a missing file as `ARTIFACT_PURGED`.
   - An `ArtifactStoreException` on one key is logged with the key and counted in `failedKeys`. That key stays
     unpurged and the remaining keys continue. Repository failures propagate.
   - Time comes from `RunClock`.
   - `RetentionRulesTest` covers:
     - max-age boundaries
     - newest-N seasons per source, independent per source
     - shared keys aged by the oldest row
     - active-run protection
     - an empty input
   - `ArtifactCleanupTest` covers a delete failure, the crash-between-steps rerun and already purged rows being
     ignored.
   - New `test-jar` fixture: `InMemoryArtifactRetentionRepository`. `InMemoryArtifactStore` gains a failure toggle
     for `delete`.

## Orchestrator runtime

8. **Migration `V9__replay_and_retention.sql`.**
   - `run_artifact`:
     - add `purged_at timestamptz NULL` with `CHECK (purged_at IS NULL OR purged_at >= created_at)`
     - add an index `ix_run_artifact_storage_key (storage_key)`
     - add a partial index `ix_run_artifact_unpurged (created_at) WHERE purged_at IS NULL`
   - `pipeline_step`:
     - add `import_job_reused boolean NULL`
     - add `ck_pipeline_step_reused_import_only CHECK (import_job_reused IS NULL OR kind = 'IMPORT')`
   - No change to `pipeline_run`: `RETRY`/`retry_of_run_id` already exist, and the active-run unique index already
     covers replays.
   - Rows written before `V9` read `purged_at = NULL` and `import_job_reused = NULL`, which means "not recorded".
   - `ReplayRetentionMigrationTest` (Testcontainers) checks both constraints and a pre-`V9` row.

9. **Persistence adapters.**
   - `RunArtifactEntity.purgedAt` and `PipelineStepEntity.importJobReused`, with their mapping in
     `JpaRunArtifactRepository` and `JpaPipelineStepRepository`.
   - `JpaRunArtifactRepository.findByStorageKey` and `markPurged`, the latter one JPQL bulk update.
   - New `JpaArtifactRetentionRepository`:
     - `findUnpurged` joins `run_artifact` to `pipeline_run` in JPQL (source, season, status in `RunStatus.active()`)
     - `seasonsBySource` is a `select distinct source, season`
     - no aggregation in SQL
   - Extend `JpaRunArtifactRepositoryTest` and `JpaPipelineStepRepositoryTest`, and add
     `JpaArtifactRetentionRepositoryTest`.

10. **Configuration (`PipelineOrchestratorProperties`, `application.yml`).**
    - New optional `tt.pipeline.retention` block: `cron` (Spring six-field), `zone` (IANA) and `rules.<kind>` with
      exactly one of `max-age` (ISO-8601 duration) or `seasons` (1-10).
    - Without the block there is no cleanup job and artifacts are kept, as today. This is opt-in like the schedules,
      with no default cron, zone or rule.
    - With the block, startup fails naming the setting for any of these:
      - a missing or invalid cron or zone
      - a kind without a rule, or with both or neither of `max-age` and `seasons`
      - a value out of range
    - `PipelineSettingsConfiguration` maps it to the core `RetentionPolicy`.
    - `application.yml` documents the block commented out, for example `zip: seasons: 1`, `manifest: seasons: 1`,
      `raw: max-age: P90D` and `json: max-age: P90D`.
    - Extend `PipelineOrchestratorPropertiesTest`.

11. **Cleanup job (`<rt>/artifact/ArtifactCleanupSchedule.java`, `ArtifactRetentionConfiguration.java`).**
    - It is a `SmartLifecycle` with a private single-thread `ThreadPoolTaskScheduler` (not a bean) on the configured
      cron and zone. It runs `ArtifactCleanup.run()` under the ShedLock lock `pipeline-artifact-cleanup` through
      `LockingTaskExecutor`.
    - The outcome is logged at INFO (keys and bytes purged, failed keys). A tick failure is logged as a warning, and
      the next tick tries again.
    - It is registered only when `tt.pipeline.retention` is present.
    - There is no `@EnableScheduling`, `Executor` bean or startup catch-up, and the job is never triggered by a run.
    - `ArtifactCleanupScheduleTest` follows `DailyStatsScheduleTest`.

12. **Replay API (`<rt>/api`).**
    - `POST /api/pipeline/runs/{id}/replay`, with no body, in `RunsController`, which only translates to
      `ReplayRun.replay(id, CurrentUser.name(auth))`. The responses:
      - `201` with `RunSummaryDto` of the new `RETRY` run and a `Location` header
      - `404` for an unknown run (`RunNotFoundException`)
      - `409` problem with `code: ACTIVE_RUN` and `activeRunId`
      - `422` problem with `code` set to `RUN_ACTIVE`, `NO_PACKAGE` or `ARTIFACT_PURGED`
    - `SecurityConfiguration`: add `requestMatchers(HttpMethod.POST, "/api/pipeline/runs/*/replay")
      .hasAuthority(TRIGGER_AUTHORITY)`. Without it the path would fall to `anyRequest().authenticated()`.
    - DTOs:
      - `RunDetailDto` gains `replay: { allowed, code }`, from `ReplayRules.check` in `RunQueryService`
      - `ArtifactDto` gains `purgedAt`
      - `StepDto` gains `importJobReused`
      - `RunSummaryDto` gains `importJobReused`: the flag of the `IMPORT` step whose `externalRef` is the run's
        `importJobId`, null when there is none
    - Update `RunDtoMapper` and the OpenAPI annotations.
    - Tests:
      - `RunsApiWebTest`: each status code, the `403` without `matches:write` and the `401` without a token
      - `RunDtoMapperTest`: the new fields

13. **Run events.** The created replay run reaches the event stream through the existing `RunObserver ->
    RunEventBroadcaster` path, with no new event type. Add one `RunEventBroadcasterTest` case for a `RETRY` run
    summary.

14. **End-to-end replay test (`RunExecutorHttpTest` or a new `ReplayHttpTest`).**
    - Run an original against `StubHttpServer` (ingest and platform), then replay it.
    - Assert that the stub saw no ingest call for the replay and one `POST /import/jobs` with the same ZIP bytes.
    - Assert that a `200 {created:false}` answer ends the replay `SUCCEEDED` with `importJobReused = true` and the
      original's job id.
    - Persistence variant in `RunExecutionPersistenceTest`, with Docker running.

## Orchestrator frontend

15. **API and types.**
    - `<fe>/api/types.ts`: `ReplayEligibility`, `RunDetail.replay`, `Artifact.purgedAt`, `Step.importJobReused` and
      `RunSummary.importJobReused`.
    - `<fe>/api/runs.ts`: `replayRun(client, id)`, which resolves `201` and rejects `409`/`422` with an `ApiError`
      carrying the problem `code`.
    - Bind it in `bindApi.ts` and cover it in `endpoints.test.ts`.

16. **Replay action on the run detail page.**
    - New `<fe>/runs/ReplayRunDialog.tsx`:
      - The confirmation text says that ingest is skipped, that the stored ZIP of the run is re-submitted, and that
        the platform returns the existing job when it already imported the same content successfully.
      - On `201` it navigates to the new run. A `409`/`422` shows the server message in the dialog.
    - `RunDetailPage` shows a "Replay import" button, wrapped in `<Can capability="trigger-runs">`:
      - It is enabled only when `run.replay.allowed`.
      - Otherwise it is disabled with a tooltip per `replay.code`, mapped in `<fe>/runs/replay.ts` (labels only, never
        derived).
    - `RunActivityLog` and `useRunDetail` are unchanged, and the screen opens no event connection of its own.

17. **Showing replays and reuse.**
    - `TRIGGER_LABELS.RETRY` becomes "Replay", and the header link reads "Replay of <id>".
    - When `importJobReused` is true, the header and the import-report section show the chip "Existing import job
      reused — nothing was re-imported".
    - The artifacts table shows "Purged <date>" for purged rows.
    - `StepBadges` shows the `INGEST` and `FETCH_PACKAGE` badges of a `RETRY` run as "skipped (replay)" instead of
      "—".

18. **Frontend tests (Vitest + RTL).**
    - `RunDetailPage.test.tsx`:
      - the button is enabled, disabled per code, and hidden without `matches:write`
      - the dialog flow, with navigation on `201` and the message on `409`/`422`
      - the reused chip and the purged artifact row
    - `runStatus.test.ts`: the label change.
    - Extend `src/test/runFixtures.ts`.

## Documentation and validation

19. **Documentation.**
    - `tt-league-pipeline-orchestrator-runtime/docs/pipeline-datamodel.md`:
      - the `V9` columns and indexes
      - the transition `QUEUED -> PACKED (RETRY only)` in the transition table
      - the `ARTIFACT_PURGED` failure code
      - `import_job_reused` in "Written by the run executor"
      - the shared-key rule for replay `ZIP` rows
      - the purge semantics: the file is deleted, the row is kept with `purged_at`
    - Runtime `README.md`: the replay endpoint, the `tt.pipeline.retention` block with an example, and the note that
      artifacts are kept forever without it.
    - Module `AGENTS.md` files:
      - core: `ReplayRun` is the only creator of `RETRY` runs, `RetentionRules`/`ArtifactCleanup` are the only purge
        path, plus the new `test-jar` fixtures
      - runtime: `ArtifactCleanupSchedule` (private scheduler, ShedLock `pipeline-artifact-cleanup`), and artifacts
        are deleted only through `ArtifactCleanup`
      - frontend: the replay eligibility comes from the server
    - Frontend `README.md`, if it lists the run detail actions.

20. **Validation.**
    - `mvn -pl tt-league-pipeline-orchestrator-core -am test` (including `CoreDependencyRulesTest`)
    - `mvn -pl tt-league-pipeline-orchestrator-runtime -am test`, with Docker running for the Testcontainers tests
    - `mvn -pl tt-league-pipeline-orchestrator-frontend -am test`
    - the full `mvn test`

    Then review the diff for `target/`, `node_modules/`, `dist/` and generated content.

## Acceptance Criteria

- [x] Operators can replay the import of a past run from the API and the runs view; it creates a `RETRY` run linked to the original
- [x] Artifacts follow a configurable retention policy (for example ZIPs for the season, raw files 90 days), enforced by a cleanup job
- [x] Replaying an unchanged ZIP returns the existing import job (idempotency) and the run records that
- [x] Tests cover replay, retention and the idempotent case

# Implementation Guidelines

Follow the root and module `AGENTS.md` files.

- **Core boundaries.** The core stays framework-free, and `CoreDependencyRulesTest` must pass.
  - `ReplayRules` is the only place that decides whether a run can be replayed, and `RetentionRules` the only place
    that decides what expires. Both are pure, with no I/O and no clock. The runtime and the UI show their answers and
    never re-derive them.
- **Run creation.** `ReplayRun` is the only creator of `RETRY` runs, and `TriggerRun` stays the only creator of
  `MANUAL`/`SCHEDULED` runs. Both launch only through `RunLauncher`.
  - A replay never bypasses the one-active-run-per-source rule and is never queued as a pending trigger.
- **Replays reuse the original package.** A replay never calls ingest and never downloads from the federation. It
  re-submits the original's stored ZIP, re-hashed against its recorded `sha256` first.
  - It shares the original's storage key. The file is never copied or rewritten.
  - The scope, season, source and `force` flag are copied from the original for display only. They do not change
    what is imported.
- **Platform deduplication is respected** (2026-10-05 decision). A reused job is a normal result, recorded as
  `importJobReused = true`, and is not an error.
  - Never pass `allowPublishedShrink=true` on a replay. A replay of an `IMPORT_SHRINK` failure fails the same way.
  - No platform module is changed.
- **Purging.** Cleanup deletes files, never rows: run and artifact rows are history, and the purge only sets
  `purged_at`.
  - Files are deleted only through `ArtifactCleanup`, and never while any run that references the key is active.
  - A failed delete leaves that key unpurged. It is not success-shaped: it is logged and counted.
- **Retention is opt-in.** Without `tt.pipeline.retention` nothing is deleted, which is today's behaviour.
  - With the block, every artifact kind needs an explicit rule, and invalid values fail startup naming the setting.
    There is no default cron, zone or rule.
- **Schema.** Changes go only through `V9__replay_and_retention.sql`, with `docs/pipeline-datamodel.md` updated in the
  same change. There are no foreign keys into platform tables.
- **Scheduling.** There is no `@EnableScheduling`, scheduler bean or `Executor` bean. The cleanup job uses its own
  private scheduler and the ShedLock lock `pipeline-artifact-cleanup`.
- **Replays count as runs.** Statistics, alerts, the tracker and polling count replay runs like any other run, with no
  special-casing. A replay has no `INGEST` step, so it adds nothing to source health.
- **Out of scope:**
  - re-parsing stored raw files. That needs an ingest "parse only from stored content" option, which is out of scope
    unless it is added to the ingest service.
  - storing `RAW`/`MANIFEST`/`JSON` artifacts. Only `ZIP` artifacts are written today; the retention rules for the
    other kinds apply once a writer exists.
  - a platform "force re-import" option that bypasses the `contentSha256` deduplication
  - replaying with `allowPublishedShrink`
  - bulk or multi-run replay, and a replay action in the runs list
  - restoring purged artifacts

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Audit and replay".

- 2026-10-05: the plan was built against the code on branch `feature/incremental-actas-by-jornada`, after FEAT-00113.
  The effort was raised from medium to large: 20 steps across the orchestrator core, runtime and frontend.
- 2026-10-05 decision (user): replay is orchestrator-only and respects platform deduplication. Consequence: for an
  original whose import ended `SUCCEEDED`/`PARTIAL`, the replay returns that job and nothing is re-imported. A
  "fix, then replay" re-imports only originals whose import `FAILED`, was rejected or never ran.
- Follow-up candidate (not planned): a platform option on `POST /administration/import/jobs` that skips the
  `contentSha256` deduplication, with a "Force re-import" toggle in the replay dialog. It would touch
  `tt-data-league-core-domain` (`ImportJobService.submit`) and `tt-data-league-api-rest` (`ImportJobController`).
- Open question: the `seasons` retention rule counts the seasons that have orchestrator runs for the source. When a
  new season's first run is created, the previous season's ZIPs expire under `seasons: 1`. Operators who want them to
  stay through the season change should configure `seasons: 2`. Confirm the recommended default for the README
  example during implementation.
- 2026-10-05: the plan was approved and the feature marked `ready` (user request). The README-example question above
  does not block implementation.
- 2026-10-05: implemented (core, runtime, frontend, docs). Validation: core 446+ tests green including `CoreDependencyRulesTest`;
  runtime tests green except the pre-existing `PipelineOrchestratorPropertiesTest.failsWhenTheStatisticsZoneIsMissingBlankOrInvalid`
  (expects "is not a valid time zone", the code says "is not a valid IANA time zone id"; not touched by this feature);
  frontend 402 tests, `tsc` and `eslint` green. The Testcontainers tests (145 skipped: `ReplayRetentionMigrationTest`,
  `JpaArtifactRetentionRepositoryTest`, the extended `JpaRunArtifactRepositoryTest`/`JpaPipelineStepRepositoryTest`) were
  written but NOT run, because Docker is not available in the implementation environment. The persistence variant of step 14
  (`RunExecutionPersistenceTest`) was not added for the same reason; the HTTP end-to-end test is `ReplayHttpTest`. Run them
  with Docker before closing the feature. The full `mvn test` also fails in `tt-data-league-import`
  (`BcnesaImportProcessorsTest`: missing fixture `acta_bcnesa_2026_published.json`), which this feature does not touch.
- 2026-10-05: implementation choices. `ArtifactRetentionConfiguration` is conditional on `tt.pipeline.retention.cron`; the
  `seasons` rule keeps a season unknown to `seasonsBySource` (never purge on missing information); `max-age` expires an
  artifact once its age reaches the duration. A run event never carries `importJobReused`, so `useRunDetail` keeps the
  value it already had. README example uses `seasons: 2` (resolves the open question above: it keeps the previous season's
  ZIPs through the season change).

