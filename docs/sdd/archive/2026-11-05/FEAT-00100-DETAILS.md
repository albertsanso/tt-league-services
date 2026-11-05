# Build Plan
Paths below abbreviate `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core` as `<core>`.
Steps 1-6 are domain/application work and compile without the adapters; steps 7-9 wire them.

1. **Remove dead types.** Delete `<core>/domain/shared/port/ImportJobsPort.java` and
   `<core>/domain/shared/model/ImportJob.java`, `ImportJobRequest.java`, `ImportJobStatus.java` (no Java or
   frontend references; rechecked 2026-10-04 after FEAT-00099). The only other mentions are prose in
   `tt-data-league-api-runtime/README.md` (line ~93) and `tt-data-league-import-runtime/README.md` (line ~346);
   step 11 rewrites them.
2. **Extract the run body into the domain (`<core>/domain/load/service/ImportResourceRunService.java`, new
   `@Named`).** It only needs domain types (`ImportResourceRepository`, `ImportResourceProcessService`,
   `ImportRunRegistry`, `Clock`), so it lives in the domain where `ImportJobService` (step 5) can use it without
   depending on the application layer. Constructors follow `StartImportProcessCommandHandler`: an `@Inject` one
   using `Clock.systemDefaultZone()` and one taking a `Clock`.
   - `void markProcessing(ImportResource resource)`: `setPending()`, `startProcessing()`, `save` (the current
     `accept` preamble).
   - `ImportRunSnapshot run(UUID runId, ImportResource resource)`: the current `runAsync` body (mark running,
     `process` with progress updates, `finishProcessing`, `complete`, and the `RuntimeException` branch). Returns
     the terminal snapshot from `runRegistry.findByRunId(runId)`; it throws `IllegalStateException` when the
     registry has lost the run.
   - Move `progressFrom` and `safeMessage` with it. `StartImportProcessCommandHandler` keeps validation,
     `registerQueued` and `rejectSubmission`, calls `markProcessing`, and submits `runService.run(...)` to its
     executor. Its public constructor signatures do not change: it builds its own
     `new ImportResourceRunService(repository, service, runRegistry, clock)` (the service is stateless, so a second
     instance next to the `@Named` bean is harmless). `StartImportProcessCommandHandlerTest` (14 construction sites)
     must pass unchanged. Add `ImportResourceRunServiceTest` (success, empty result, failure result, thrown
     exception).
3. **Split upload validation from storage (`<core>/domain/load/service/`).**
   - `ResourceUploadService.validateUpload(String filename, byte[] content, boolean allowShrink)` returns the
     `ImportManifest`: `validateFile`, `extractZipAndGetManifest` (which already checks `contentSha256` since
     FEAT-00099), then `verifyPublishedActasNotShrinking`. `uploadAndTriggerAsyncLoad` keeps its exact behaviour
     by calling `validateUpload` and then `triggerAsyncLoad`.
   - Add `ResourceUploadService.readManifest(String filename, byte[] content)` (validate file + extract, no shrink
     check) so the job can read the manifest and check deduplication before the shrink check (step 5).
   - `ResourceRepositoryLoaderService.loadIntoRepository` returns `List<ImportResource>`: the ACTAS import
     resources of the manifest's seasons, in manifest season order, as returned by
     `createResourcesAndStartProcessing`; empty for a TEAMS-only manifest. Existing callers ignore the result.
     Extend `ResourceRepositoryLoaderServiceTest` for the returned list.
   - Add `public void deleteExtractionFolder(ImportManifest)` (recursive delete, `UncheckedIOException` on
     failure) to `ResourceZipService` for the job's cleanup; the manual path is unchanged.
4. **Run registry: busy query.** Add `boolean hasActiveRun()` to `ImportRunRegistry` (true when any run is
   `QUEUED`/`RUNNING`). Implement it in `tt-data-league-api-runtime/.../importrun/InMemoryImportRunRegistry.java`
   and in the test fake in `StartImportProcessCommandHandlerTest`; cover it in `InMemoryImportRunRegistryTest`.
   It is a best-effort pre-check before storing files; `registerQueued` stays the atomic gate.
5. **Job domain (`<core>/domain/load/job/`, new package; no Spring, `javax.inject` only).**
   - `ImportJobStatus`: `QUEUED`, `STORING`, `IMPORTING`, `SUCCEEDED`, `PARTIAL`, `FAILED`; `isTerminal()`,
     `isActive()` (`QUEUED`/`STORING`/`IMPORTING`).
   - `ImportJobSeason`: `season` (`String`, manifest form), `importResourceId`, `importRunId` (optional until
     registered), `status` (`ImportRunStatus`), `result` (`Optional<ImportProcessResult>`), `errorDetail`
     (optional). Mutable only through the aggregate.
   - `ImportJob` aggregate: `id`, `source` (`ImportSource`), `seasons` (`List<String>`), `mode` (`UploadMode`),
     `contentSha256` (optional), `clientRunId` (optional, the `runId` request parameter), `manifestRunId`
     (optional, `provenance().runId()`), `allowPublishedShrink`, `stagedZipPath`, `requestedBy`, `status`,
     `errorDetail`, `createdAt`, `startedAt`, `finishedAt`, `List<ImportJobSeason>`. Factory `queued(...)`;
     transitions `startStoring`, `startImporting`, `addSeason`, `recordSeason`, `finish(status, errorDetail)`,
     `interrupt(reason)`; each rejects an illegal move with `IllegalStateException`.
   - `ImportJobRepository` port: `save`, `findById`, `findActiveOrSucceededBySourceAndContentSha256(source, sha)`
     (statuses `QUEUED`/`STORING`/`IMPORTING`/`SUCCEEDED`/`PARTIAL`, most recent first), `find(Optional<source>,
     Optional<from>, Optional<to>, limit)` ordered by `createdAt` descending, `findByStatusIn(Set<ImportJobStatus>)`
     ordered by `createdAt` ascending.
   - `ImportJobDispatcher` port: `void dispatch(UUID jobId)`; implemented in the runtime (step 8).
   - `ImportJobSettings` record (`Duration busyRetryInterval`, `Duration busyTimeout`; both positive) and a
     `Sleeper` functional interface (`sleep(Duration)`, default `Thread::sleep`) so tests run without waiting.
   - `ImportJobService` (`@Named`, constructor-injected `ResourceUploadService`, `ResourceZipService`,
     `ResourceRepositoryLoaderService`, `ImportRunRegistry`, `ImportResourceRunService`,
     `ImportResourceRepository`, `ImportJobRepository`, `ImportJobDispatcher`, `ImportJobSettings`, plus a
     `Clock`/`Sleeper` test constructor):
     - `SubmitResult submit(filename, bytes, clientRunId, allowShrink, requestedBy)` is `synchronized` (a single
       instance per JVM, like the run registry) so two identical concurrent submits cannot both create a job.
       Order: `readManifest` (400) -> when `contentSha256` is present, `findActiveOrSucceededBySourceAndContentSha256`
       returns the existing job as `SubmitResult(job, created=false)` -> `verifyPublishedActasNotShrinking` (409)
       -> write the bytes to `<import folder>/import-jobs/<jobId>.zip` -> save `QUEUED` -> `dispatch(jobId)`.
       The extraction folder is always deleted before returning. `clientRunId`, when given, must match
       `[A-Za-z0-9._-]{1,64}` (400). A dispatch failure marks the job `FAILED` and is rethrown.
     - `void execute(UUID jobId)`: ignores a job that is not `QUEUED`. Then, as one guarded sequence:
       1. Wait while `hasActiveRun()`, sleeping `busyRetryInterval`, up to `busyTimeout` measured from the start of
          the wait; on timeout the job ends `FAILED` with "Timed out after <busyTimeout> waiting for another import
          to finish".
       2. `STORING`: re-extract the staged ZIP (`readManifest`), re-run `verifyPublishedActasNotShrinking` with the
          job's `allowPublishedShrink` (a shrink here ends the job `FAILED` with the exception message), then
          `loadIntoRepository`.
       3. `IMPORTING`: for each returned import resource, add a season row, then wait for
          `registerQueued` with the same busy loop (on timeout the season is `FAILURE` with the timeout reason and the
          remaining seasons are still attempted). A resource already `PROCESSING` makes the season `FAILURE` with
          "Import resource <id> is already processing". Otherwise `markProcessing`, `run`, and record the terminal
          snapshot (run id, status, result, error) and save the job after every season.
       4. Final status: no seasons, or every season `SUCCESS`/`EMPTY_RESULT` with `processorFailures == 0` and no
          `executionIssues` -> `SUCCEEDED`; at least one season `SUCCESS`/`EMPTY_RESULT` otherwise -> `PARTIAL`;
          no successful season -> `FAILED`.
       Any unexpected `RuntimeException` ends the job `FAILED` with its message (logged with the job id). A
       `finally` block deletes the staged ZIP and the extraction folder.
     - `void recoverAfterRestart()`: for each `STORING`/`IMPORTING` job, every season without a terminal status
       whose import resource is `PROCESSING` gets `finishProcessing(false, now)` (back to `ERROR`, so later jobs
       and manual starts can import that season), then the job is `interrupt`ed (`FAILED`, "Interrupted by a
       platform restart") and its staged ZIP deleted. Then every `QUEUED` job is dispatched in creation order.
       Only resources recorded on the job's own season rows are touched.
6. **Application layer (`<core>/application/importjob/`, new).**
   - `SubmitImportJobCommand` + handler (calls `ImportJobService.submit`; maps `IllegalArgumentException` to a
     fail response with reason `INVALID`, `SnapshotShrinkException` to `SHRINK`), `FindImportJobQuery` + handler,
     `FindImportJobHistoryQuery` + handler (validates `limit` 1..200, default 50, and `from <= to`).
   - DTOs `ImportJobDto` (`importJobId`, `status`, `source`, `seasons`, `mode`, `contentSha256`, `runId` (client),
     `manifestRunId`, `allowPublishedShrink`, `requestedBy`, `errorDetail`, `createdAt`, `startedAt`,
     `finishedAt`, `seasonResults`), `ImportJobSeasonDto` (`season`, `importResourceId`, `importRunId`, `status`
     as `ImportRunStatus.value()`, `errorDetail`, `result` as `ImportProcessResultDto`), and
     `ImportJobAcceptedDto` (`importJobId`, `status`).
   - Make `ImportProcessResultDtoMapper` and its `toDto(ImportResource, ImportProcessResult)` public, and add a
     public overload taking `(UUID importResourceId, String source, String season, String type, ImportProcessResult)`
     so the job mapper does not need to load the `ImportResource`.
7. **JPA adapter (`tt-data-league-core-repository-jpa/src/main/java/org/cttelsamicsterrassa/data/core/repository/jpa/load/`).**
   - `model/ImportJobJPA` (table `import_job`: `id` UUID PK, `source`, `seasons` (comma-joined `TEXT`), `mode`,
     `content_sha256` (nullable, 64), `client_run_id`, `manifest_run_id`, `allow_published_shrink`,
     `staged_zip_path`, `requested_by`, `status` (enum string), `error_detail` (`TEXT`), `created_at`,
     `started_at`, `finished_at`) with `@OneToMany(mappedBy = "job", cascade = ALL, orphanRemoval = true)
     @OrderColumn(name = "position")` seasons.
   - `model/ImportJobSeasonJPA` (table `import_job_season`: `id` UUID PK, `job_id` FK to `import_job`, `position`,
     `season`, `import_resource_id` and `import_run_id` without FKs, `status`, `error_detail`, `result_json`
     (`TEXT`)).
   - Indexes `idx_import_job_source_sha (source, content_sha256)`, `idx_import_job_created (created_at)`,
     `idx_import_job_status (status)`.
   - `impl/ImportJobRepositoryHelper` (Spring Data, with the queries for the port), `impl/ImportJobRepositoryJpa`
     (`@Named`, implements the port), and `mapper/ImportJobToImportJobJPAMapper` /
     `ImportJobJPAToImportJobMapper` following the `ImportResource` mappers. `result_json` is the Jackson
     serialization of the domain `ImportProcessResult` via the injected `ObjectMapper`; an unreadable value is an
     `IllegalStateException` naming the job and season.
   - The schema is created by `ddl-auto: update` (runtime) and `create-drop` (tests); no migration tool exists.
8. **Runtime (`tt-data-league-api-runtime/src/main/java/org/cttelsamicsterrassa/data/api/runtime/`).**
   - `config/ImportJobProperties` (`tt.league.import.jobs.busy-retry-interval`, default `PT10S`; `busy-timeout`,
     default `PT2H`; both must be positive, fail at startup otherwise) with `toSettings()`; `application.yml`
     binds `IMPORT_JOBS_BUSY_RETRY_INTERVAL` / `IMPORT_JOBS_BUSY_TIMEOUT`.
   - `importjob/ExecutorImportJobDispatcher` implements `ImportJobDispatcher` with a **private** single-thread
     `ExecutorService` (named thread `import-job-1`) that calls `ImportJobService.execute`, shut down on
     `@PreDestroy`. Do **not** expose it as an `Executor`/`TaskExecutor` bean: Spring Boot 3.5's
     `applicationTaskExecutor` backs off when any `Executor` bean exists, and `StartImportProcessCommandHandler`
     and `ResourceUploadService` inject the plain `Executor`, so a new bean would silently move manual imports onto
     the job thread. To avoid a construction cycle (`ImportJobService` -> dispatcher -> `ImportJobService`) the
     dispatcher takes an `ObjectProvider<ImportJobService>`.
   - `config/ImportJobConfiguration`: `@EnableConfigurationProperties(ImportJobProperties.class)`, the
     `ImportJobSettings` bean, and an `ApplicationReadyEvent` listener calling `recoverAfterRestart()`.
9. **REST (`tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/importjob/ImportJobController.java`, new).**
   `@RestController @RequestMapping(API_BASE_PATH_V1 + "/administration/import/jobs")`,
   `@PreAuthorize("hasRole('ADMIN')")` (FEAT-00101 changes it to `imports:write`).
   - `POST` multipart (`file`, optional `runId`, `allowPublishedShrink` default false): empty file -> 400; 202
     `ImportJobAcceptedDto` for a new job, 200 with the same shape for a deduplicated one, 400 invalid ZIP,
     manifest, hash or `runId`, 409 shrink, 500 when the upload bytes cannot be read. `requestedBy` is
     `Authentication.getName()`.
   - `GET /{id}` -> 200 `ImportJobDto` / 404.
   - `GET` with optional `source` (`ImportSource` name, 400 when unknown), `from`/`to` (ISO-8601 dates on
     `createdAt`, UTC, both inclusive), `limit` (default 50, 1..200, 400 otherwise) -> 200 list, newest first.
   - OpenAPI `@Operation` annotations as in `ImportResourceController`.
10. **Tests (JUnit 5, existing styles).**
    - Domain: `ImportJobTest` (legal and illegal transitions), `ImportJobServiceTest` with in-memory fakes for the
      job repository, dispatcher and registry plus a recording `Sleeper`: submit 202 path stages the ZIP and
      dispatches; dedupe returns the existing job for active/`SUCCEEDED`/`PARTIAL` and creates a new one after
      `FAILED` or without `contentSha256`; dedupe wins over the shrink check; shrink -> `SnapshotShrinkException`;
      invalid `runId`; execute `SUCCEEDED`/`PARTIAL`/`FAILED` mapping including a mixed-season job; busy wait
      succeeds after retries and times out with the reason; season with a `PROCESSING` resource fails; staged ZIP
      and extraction folder deleted on success and failure; `recoverAfterRestart` fails interrupted jobs, resets
      only their own `PROCESSING` resources and re-dispatches `QUEUED` jobs in order.
    - Application: handler tests for the fail reasons, history validation and DTO mapping.
    - JPA: `load/ImportJobRepositoryJpaTest` round trip (seasons order, `result_json` with lifecycle counters and
      round progress) and each query.
    - Runtime: `ImportJobPropertiesTest` (defaults, binding, rejection of non-positive values); a dispatcher test
      showing jobs run one at a time; a context check that the auto-configured `applicationTaskExecutor` is still
      the injected `Executor`.
    - REST: `ImportJobControllerTest` (202/200/400/409/404, listing parameters and validation) in the style of
      `ImportResourceControllerTest`; existing `ImportResourceControllerTest` unchanged and green.
11. **Docs.**
    - `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`: `import_job` and `import_job_season` sections
      (columns, indexes, cascade/orphan removal, no FK from `import_resource_id`/`import_run_id` because season rows
      are snapshots of what ran and run ids live in the in-memory registry), plus the entity relationship summary.
    - `tt-data-league-api-runtime/README.md`: the jobs API (endpoints, status codes, statuses and their mapping,
      deduplication rule, one-job-at-a-time and busy wait, restart behaviour), the two configuration variables, and
      replace the `ImportJobsPort` sentence (line ~93) with the job persistence that now exists.
    - `tt-data-league-import-runtime/README.md`: drop the `ImportJobsPort` reference (line ~346).
12. **Validation.** `mvn -pl tt-data-league-api-runtime -am test`, then the full `mvn test` from the root. Report the
    pre-existing `BcnesaImportProcessorsTest.storesTheSetScoresOfEveryGameFromTheHtmlBasedActas` fixture failure
    (FEAT-00099 notes) if it still occurs.

## Acceptance Criteria

- [x] `POST /api/v1/administration/import/jobs` (multipart `file`, optional `runId`, `allowPublishedShrink`) validates the ZIP synchronously (400 invalid, 409 published-acta shrink) and returns `202 {importJobId, status}`
- [x] A job stores the ZIP content and then imports every ACTAS season of its manifest, moving through `QUEUED`, `STORING`, `IMPORTING` and ending `SUCCEEDED`, `PARTIAL` or `FAILED`
- [x] `GET /api/v1/administration/import/jobs/{id}` returns the job with, per season, the import run id, status and `ImportProcessResult` (counters, lifecycle counters, round progress); `GET /api/v1/administration/import/jobs?source=&from=&to=&limit=` lists jobs, most recent first
- [x] When the manifest has `contentSha256`, submitting the same source and hash as a `SUCCEEDED`/`PARTIAL` or active job returns that job with 200 and no new import; without `contentSha256` there is no deduplication
- [x] Jobs run one at a time system-wide; a job waits (bounded, configurable) while a manually started import is active and fails with a clear reason after the timeout
- [x] Jobs are persisted in `import_job` and `import_job_season`; after a restart `QUEUED` jobs resume and `STORING`/`IMPORTING` jobs end `FAILED` with an interruption reason, returning the import resource they left `PROCESSING` to `ERROR`; `rfetm-datamodel.md` documents both tables
- [x] The existing upload, preview and start endpoints behave as before, and the unused `ImportJobsPort`/`shared.model.ImportJob*` types are removed

# Implementation Guidelines

- Keep `POST /administration/import/upload` and the preview/start endpoints unchanged for manual use.
- Reuse the import run machinery (`ImportRunRegistry`, `ImportResourceProcessService`, `ImportProcessResult`);
  the job only chains storage and runs. Do not duplicate import logic.
- Natural-key upsert, `id_partido` and amended-acta behaviour are unchanged.
- Domain code stays free of Spring: the executor and startup listener live in `tt-data-league-api-runtime`.
- Domain classes the job uses (`ImportResourceRunService`, `ImportJobService`) live in `domain/load/...`; the
  application layer depends on the domain, never the reverse.
- Never declare a new `Executor`/`TaskExecutor` bean for jobs; it would replace Spring Boot's
  `applicationTaskExecutor` that the manual upload and start paths inject.
- Restart recovery only touches import resources recorded on an interrupted job's own season rows. It does not
  repair resources left `PROCESSING` by an interrupted *manual* run.
- Out of scope: multi-instance deployment (the run registry and the submit lock are per JVM), cancelling a job,
  retrying a failed job (resubmit instead), and retention/pruning of old `import_job` rows.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Changes to the Java backend import" items 1-3. Today an upload returns 202 without an id and only
stores files and marks the import resource pending, so an automated client cannot follow it. "Results corrected"
in the report can reuse the amended-acta detection counters (FEAT-00089) when that option is on.

## Planning notes (2026-10-04)

- Today `uploadAndTriggerAsyncLoad` stores files in a fire-and-forget task and only marks the import resource
  `PENDING`. The import itself needs `POST /start` per resource, and its status lives in
  `InMemoryImportRunRegistry`, lost on restart. The job envelope closes both gaps without changing those paths.
- Imports are single-run **system-wide** (`ImportRunRegistry.registerQueued` rejects while any run is active), so the
  "one job per source" idea from the backlog draft became "one job at a time system-wide" plus a bounded wait for
  manual imports. Acceptance criteria were updated accordingly.
- The job stages the uploaded bytes and re-extracts at execution because the extraction folder is a temporary
  directory that does not survive a restart.
- Deduplication relies on the platform-verified `contentSha256` from FEAT-00099; manifests without it are never
  deduplicated (explicit, no fallback hash).
- An `ImportResource` left `PROCESSING` by a crash is an existing issue of the manual path too; it is not addressed here.
- Removing `ImportJobsPort` and the `shared.model.ImportJob*` records is safe: they have no references anywhere and
  their names would clash with the new job model.

## Plan rebuild (2026-10-04, after FEAT-00099)

The plan was checked against the code after FEAT-00099 merged (`748362a`). Changes and why:

- **`ImportResourceRunService` moves to `domain/load/service`.** The previous plan put it in
  `application/importresource/process`, which would make the domain `ImportJobService` depend on the application
  layer. It only needs domain ports, so it belongs in the domain. The handler builds its own instance, so its
  constructors and its 14 test construction sites stay unchanged.
- **No new `Executor` bean.** Neither `StartImportProcessCommandHandler` nor `ResourceUploadService` names an
  executor; they get Spring Boot's auto-configured `applicationTaskExecutor`, which backs off as soon as any
  `Executor` bean exists (Boot 3.5.8). The job dispatcher therefore owns a private single-thread executor.
- **Wait before storing, not only before importing.** `loadIntoRepository` replaces the season folder that a
  running manual import may be reading, so the job now waits for `hasActiveRun()` to be false before `STORING`
  (new port method, best effort) as well as before each `registerQueued`.
- **Deduplication runs before the shrink check.** If the shrink check ran first, resubmitting an already
  imported ZIP after newer data arrived would get 409 instead of the existing job. `readManifest` was split out for
  this.
- **Restart recovery resets the job's own `PROCESSING` resource to `ERROR`.** Otherwise `ImportResource.setPending`
  throws for that season and every later job and manual start for it fails until someone repairs the database.
  The acceptance criterion about restarts now says so. The earlier note that this was out of scope still holds for
  manual runs.
- **`PARTIAL` is defined for mixed outcomes.** It applies when at least one season succeeded but not every season was
  clean, including a season that failed or timed out. `FAILED` means no season succeeded or the job failed before
  importing.
- **The season result is stored as the domain `ImportProcessResult`** (serialized by the JPA adapter). It is
  mapped to `ImportProcessResultDto` on read, which avoids a domain-to-application dependency.
  `ImportProcessResultDtoMapper` becomes public for the job handlers.
- **Two run ids.** `runId` (request parameter, the caller's id, e.g. the orchestrator run) and `manifestRunId` (the
  ingest run id from FEAT-00099 provenance) are stored separately; correlating them is FEAT-00115.
- **The `ImportJobsPort` mentions in two READMEs** are now part of the docs step, because removing the type would
  otherwise leave stale guidance.
- The status stays `ready`: file ownership, contracts, ordering and tests are specified.

## Implementation notes (2026-10-04)

Implemented as planned, with these deviations and details:

- **Bean wiring instead of `@Named`.** `ImportJobService` and `SubmitImportJobCommandHandler` are declared as beans
  in `ImportJobConfiguration` (api-runtime), following `SeasonCalendarConfiguration`: `tt-data-league-import-runtime`
  and the JPA test application component-scan every package, and these two classes need the runtime-only
  `ImportJobDispatcher` and `ImportJobSettings`. `FindImportJobQueryHandler` and `FindImportJobHistoryQueryHandler`
  stay `@Named` (they only need `ImportJobRepository`).
- **Recovery runs in a `SmartInitializingSingleton`, not on `ApplicationReadyEvent`.** Spring Boot starts the web
  server before `ApplicationReadyEvent`, so a job submitted in that window could start `STORING` and then be failed
  as "interrupted". Recovery now runs after all singletons exist and before the server accepts requests.
- **Shutdown keeps waiting jobs.** The dispatcher thread is a daemon and `destroy()` interrupts it. A job interrupted
  while still waiting to start storing stays `QUEUED` with its staged ZIP and resumes after the restart; the staged
  ZIP is deleted only once a job is terminal.
- **Submit response** is `{importJobId, status, created}`; `created` mirrors 202 vs 200.
- **History filters** are validated in the `FindImportJobHistoryQuery` constructor (400 from the controller).
- **Schema names.** The columns are `upload_mode` and `season_index` (instead of `mode`/`position`, which are SQL
  function names), and season order uses an explicit column with `@OrderBy` rather than `@OrderColumn` on the
  inverse side.
- **`result_json`** uses a storage shape owned by `ImportProcessResultJsonCodec` (private records, season as
  `YYYY-YYYY`), not Jackson over the domain records (`RoundProgress` holds `Season`/`Year`).
- **Tests.** `ImportJobPropertiesTest` was folded into `ImportJobConfigurationTest` (defaults, binding, rejection of
  a zero duration, single `applicationTaskExecutor`, recovery called at startup). Shared domain test doubles:
  `FakeImportRunRegistry`, `FakeImportResourceRepository`.
- **Observations, not changed here.** `tt-data-league-api-runtime/README.md` mentions reviewed migrations under
  `docs/migrations/`, but no such folder exists; the schema comes from `ddl-auto: update`. The import runtime scans
  every package but defines no `ImportRunRegistry`, which `StartImportProcessCommandHandler` (`@Named`) needs, so its
  context may already fail to start; nothing tests it. This feature adds no new bean requirement to it.
- **Validation.** `mvn test` (full reactor, run with `-Dmaven.test.failure.ignore=true` so every module runs): every
  module passes except `BcnesaImportProcessorsTest.storesTheSetScoresOfEveryGameFromTheHtmlBasedActas` in
  `tt-data-league-import`, which fails independently of this feature because its fixture
  `actas/acta_bcnesa_2026_published.json` was never committed (already recorded in FEAT-00099). New suites:
  `ImportJobServiceTest` (25), `ImportJobTest` (8), `ImportResourceRunServiceTest` (7), `ImportJobHandlersTest` (6),
  `ImportJobRepositoryJpaTest` (4), `ImportJobControllerTest` (7), `ImportJobConfigurationTest` (3),
  `ExecutorImportJobDispatcherTest` (3); `StartImportProcessCommandHandlerTest` and `ImportResourceControllerTest`
  pass unchanged.
