# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>` and `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>`. Test sources mirror them under `src/test/java`. Steps 1-6 are core-only and test with fakes. Steps 7-11 add the
runtime adapters and HTTP tests. This feature changes no platform module, platform REST contract,
`tt-league-ingest` code or Flyway migration: the FEAT-00103 columns already fit every value written here.

**External contracts used (checked against the code on 2026-10-04).**
- Ingest (`tt-league-ingest-rest`, header `X-API-Key`).
  - `POST /api/v1/ingest/runs` returns `202 {runId}` (32 hex characters), `409` when the source already has an
    active ingest run, `400` for an invalid body, and `422` for an unknown scope key.
  - `GET /api/v1/ingest/runs/{runId}` returns `status` (`QUEUED`, `RUNNING`, then `SUCCEEDED`,
    `COMPLETED_WITH_ISSUES` or `FAILED`), `outcome` (null until the run ends), `retryable`, `package` (null when no ZIP
    was written) and `error`. It returns `404` for an unknown run, which includes every run from before an ingest
    restart.
  - `GET /api/v1/ingest/runs/{runId}/package` streams the ZIP with header `X-Content-SHA256`. It returns `404` when
    there is no package or the package is no longer retained, and `409` while the run is active.
  - Ingest runs one ingestion at a time across all sources, so a `QUEUED` ingest run can wait for another source's run.
- Platform (`tt-data-league-api-runtime`, service credential sent as `X-API-Key` with `imports:write`).
  - `POST /api/v1/administration/import/jobs` takes multipart `file`, `runId` (`[A-Za-z0-9._-]{1,64}`) and
    `allowPublishedShrink`. It returns `202` for a new job or `200` for a deduplicated one, both with
    `{importJobId, status, created}`. It returns `400` for an invalid ZIP, manifest or `runId`, and `409` for a
    published-acta shrink.
  - `GET /api/v1/administration/import/jobs/{id}` returns `ImportJobDto`. `status` is one of `QUEUED`, `STORING`,
    `IMPORTING`, `SUCCEEDED`, `PARTIAL` or `FAILED`. The DTO also carries `errorDetail` and
    `seasonResults[]{season, status, errorDetail, result}`, where `result` is an `ImportProcessResultDto` with the ten
    FEAT-00103 counters and `executionIssues`. Jobs are persisted, so a `404` is a real error, not a restart.

1. **Model additions (`<core>/run/`).**
   - `PipelineRun.restartIngest(String ingestRunId, Instant at)` is allowed only in `RUNNING_INGEST`. It replaces
     `ingestRunId` (non-blank, at most 64 characters) and keeps the status, `startedAt` and version. In any other
     status it throws `IllegalRunTransitionException`. A retried INGEST step uses it, so `ingestRunId` always names
     the ingest run currently being followed. It is not a status transition, and `RunStatus` is unchanged.
   - `PipelineStep.withExternalRef(String ref)` is allowed only while the step is `RUNNING` and has no `externalRef`
     yet. The ref is non-blank and at most 64 characters; otherwise it throws `IllegalStateException`. The step row is
     saved before the external call, so a crash leaves a visible `RUNNING` attempt. The ref is attached once the
     external id is known.
   - Extend `PipelineRunTest` and `PipelineStepTest` for both methods, covering legal and illegal use.

2. **Execution ports (`<core>/execution/port/`, new; JDK types only).**
   - `GatewayException extends RuntimeException`. It carries `Kind kind`, where `Kind` is `UNAVAILABLE` (HTTP 5xx,
     connection or read failure), `NOT_FOUND`, `CONFLICT`, `REJECTED` (any other 4xx) or `PROTOCOL` (unexpected status,
     body or header). It also carries an `Integer httpStatus` (nullable). The message never contains a key or header
     value.
   - `IngestGateway`:
     - `String startRun(IngestRunRequest request)`
     - `IngestRunState getRun(String ingestRunId)`
     - `FetchedPackage fetchPackage(String ingestRunId, PackageSink sink)`
   - Ingest values:
     - `IngestRunRequest(PipelineSource source, String season, IngestMode mode, RunScope scope)` with
       `IngestMode { SNAPSHOT, DELTA }`. The factory `IngestRunRequest.forRun(PipelineRun)` chooses `SNAPSHOT` for a
       full-season scope and `DELTA` otherwise, because ingest rejects a scoped snapshot that includes `package`.
     - `IngestRunState(String ingestRunId, String status, String outcome, boolean retryable, boolean
       packageAvailable, String error)`, with `boolean finished()` meaning `outcome != null`.
     - `PackageSink`, a functional interface: `StoredArtifact write(InputStream body)`.
     - `FetchedPackage(String declaredSha256, StoredArtifact stored)`.
   - `ImportGateway`:
     - `ImportSubmission submit(String fileName, ArtifactContent content, UUID clientRunId)`
     - `ImportJobState getJob(UUID importJobId)`
   - Import values:
     - `ImportSubmission(UUID importJobId, String status, boolean created)`.
     - `ImportJobState(UUID importJobId, String status, String errorDetail, List<ImportSeasonState> seasons,
       String rawJson)`, with `boolean finished()` meaning `SUCCEEDED`, `PARTIAL` or `FAILED`.
     - `ImportSeasonState(String season, String status, String errorDetail, ImportCounters counters,
       List<String> executionIssues)`, where `counters` is null while the season has no result.
     - `ImportCounters`: the ten FEAT-00103 counters, each non-negative.
   - `ArtifactStore`:
     - `StoredArtifact store(String storageKey, InputStream content)` computes the SHA-256 and size while writing,
       makes the file visible only when complete, and rejects an existing key.
     - `ArtifactContent content(String storageKey)` returns an `ArtifactContent` with `long size()` and
       `InputStream open()`, which can be opened again for each attempt.
     - `boolean exists(String storageKey)` and `void delete(String storageKey)` (idempotent).
     - `StoredArtifact(String storageKey, String sha256, long sizeBytes)`. Store failures raise the unchecked
       `ArtifactStoreException`.
   - `RunDispatcher`: `void dispatch(UUID runId)`.
   - `RunClock`: `Instant now()` and `void sleep(Duration d) throws InterruptedException`.
   - `RunObserver`: `default void runChanged(PipelineRun run)`, `default void stepChanged(PipelineStep step)`, and
     `static RunObserver none()`. This is the hook for FEAT-00105 events and the FEAT-00107 recompute after a final
     state.

3. **Settings (`<core>/execution/`).**
   - `RetryPolicy(int maxRetries, Duration initialBackoff, double multiplier, Duration maxBackoff)` validates
     `0 ≤ maxRetries ≤ 10`, `initialBackoff > 0`, `multiplier ≥ 1` and `maxBackoff ≥ initialBackoff`.
     `Optional<Duration> delayBeforeRetry(int retriesSoFar)` is empty once `retriesSoFar ≥ maxRetries`. Otherwise it
     returns `min(initialBackoff × multiplier^retriesSoFar, maxBackoff)`. The defaults wired in step 7 are 3 retries
     with `PT30S` × 2, giving waits of 30 s, 60 s and 120 s.
   - `StepTimeouts(Duration ingest, Duration fetchPackage, Duration importJob)` requires positive values and provides
     `Duration of(StepKind)`.
   - `PollIntervals(Duration ingest, Duration importJob)` requires positive values.
   - `ExecutionSettings(RetryPolicy retry, StepTimeouts timeouts, PollIntervals polls)`.
   - `FailureCode` enum. Its names are the `RunError.code` values:
     - Ingest: `INGEST_UNAVAILABLE`, `INGEST_BUSY`, `INGEST_REJECTED`, `INGEST_RUN_LOST`, `SOURCE_UNAVAILABLE`,
       `INGEST_FAILED`, `INGEST_NO_PACKAGE`.
     - Package: `PACKAGE_UNAVAILABLE`, `PACKAGE_GONE`, `PACKAGE_CHECKSUM_MISMATCH`, `ARTIFACT_STORE_FAILED`.
     - Import: `PLATFORM_UNAVAILABLE`, `IMPORT_REJECTED`, `IMPORT_SHRINK`, `IMPORT_JOB_LOST`, `IMPORT_FAILED`.
     - General: `STEP_TIMEOUT`, `PROTOCOL_ERROR`, `INTERRUPTED`, `DISPATCH_FAILED`, `INTERNAL_ERROR`.

4. **`RunExecutor` (`<core>/execution/RunExecutor.java`).** Its constructor takes the four FEAT-00103 repositories,
   `IngestGateway`, `ImportGateway`, `ArtifactStore`, `RunClock`, `RunObserver` and `ExecutionSettings`. It logs through
   `System.Logger`, so the core needs no logging dependency. `void execute(UUID runId)` loads the run, returns at once
   for a terminal run, and drives the run from its current status. That one entry point serves fresh runs and resumed
   ones. Every run change goes through `runs.update`, and the executor continues with the returned run (new version).
   Every step change goes through `steps.save`. Both notify the observer.
   - **Attempts and deadlines.**
     - A step attempt is one `pipeline_step` row. The next attempt number is the highest stored attempt of that kind
       plus 1.
     - A kind's deadline is the first attempt's `startedAt` plus `timeouts.of(kind)`, so retries and back-off waits
       count toward the timeout.
     - Every wait (poll interval or back-off) is shortened to end at the deadline. When the deadline passes, the
       running attempt fails with `STEP_TIMEOUT`, `retryable=false`, and the run fails. A timed-out step is never
       retried.
   - **Retry rule.**
     - A failed attempt is retried only when it is `retryable` and `retry.delayBeforeRetry(attempt - 1)` gives a wait
       that ends before the deadline. With the defaults, that allows at most 4 attempts per step kind. Otherwise the
       run fails with the attempt's `RunError`.
     - Retryable failures are exactly:
       - `UNAVAILABLE` from a start call (ingest `POST`, package `GET`, import `POST`);
       - the ingest outcome `SOURCE_UNAVAILABLE`;
       - `INGEST_RUN_LOST` (404 while polling);
       - more than `maxRetries` consecutive `UNAVAILABLE` polls.
     - A poll that fails with `UNAVAILABLE` is repeated after the back-off for its consecutive-failure count (the
       call-level 5xx retry). It does not create a new attempt.
     - Every other failure is final.
   - **INGEST (from `QUEUED`, or `RUNNING_INGEST` when retrying).**
     1. Save `PipelineStep.start(attempt, now, externalRef=null)`.
     2. Call `ingest.startRun(IngestRunRequest.forRun(run))`. On failure:
        - `UNAVAILABLE` → `INGEST_UNAVAILABLE` (retryable);
        - `CONFLICT` → `INGEST_BUSY`;
        - `REJECTED` / `NOT_FOUND` → `INGEST_REJECTED`;
        - `PROTOCOL` → `PROTOCOL_ERROR`.
     3. On success, save `step.withExternalRef(id)`. Then call `run.startIngest(id)` for the first attempt, or
        `run.restartIngest(id)` for later attempts.
     4. Poll every `polls.ingest`:
        - `NOT_FOUND` → `INGEST_RUN_LOST` (retryable);
        - any other non-`UNAVAILABLE` error → `PROTOCOL_ERROR`.
     5. Map the finished outcome:

        | Ingest outcome | Step | Run |
        | --- | --- | --- |
        | `NO_CHANGES` | succeeded, outcome `NO_CHANGES` | `noChanges()` (terminal) |
        | `SUCCEEDED` / `COMPLETED_WITH_ISSUES` with a package | succeeded, outcome as received | `packed()` |
        | `SUCCEEDED` / `COMPLETED_WITH_ISSUES` without a package | failed `INGEST_NO_PACKAGE` | `fail` |
        | `SOURCE_UNAVAILABLE` | failed `SOURCE_UNAVAILABLE`, `retryable=true` | retry or `fail` |
        | `FAILED` | failed `INGEST_FAILED` (message from ingest `error`) | `fail` |
        | anything else | failed `PROTOCOL_ERROR` | `fail` |

   - **FETCH_PACKAGE (in `PACKED`, no ZIP artifact yet).**
     1. The storage key is `<source lower-case>/<season>/<runId>/ingest-<ingestRunId>.zip`.
     2. Delete any leftover file at that key, start the step with `externalRef = ingestRunId`, and call
        `ingest.fetchPackage(ingestRunId, body -> artifacts.store(key, body))`. On failure:
        - `UNAVAILABLE` → `PACKAGE_UNAVAILABLE` (retryable);
        - `NOT_FOUND` → `PACKAGE_GONE`;
        - `PROTOCOL` (including a missing or malformed `X-Content-SHA256`) → `PROTOCOL_ERROR`;
        - `ArtifactStoreException` → `ARTIFACT_STORE_FAILED`.
     3. If the declared SHA-256 differs from the stored one, delete the file and fail with
        `PACKAGE_CHECKSUM_MISMATCH` (not retried).
     4. On a match, call `artifacts.add(RunArtifact(ZIP, key, sha256, size, now))` and mark the step succeeded.
   - **IMPORT (in `PACKED` with a ZIP artifact, then `IMPORTING`).**
     1. Start the step and call `importGateway.submit("<runId>.zip", artifacts.content(key), run.id())`. The
        orchestrator run id becomes the platform job's `runId`. On failure:
        - `UNAVAILABLE` → `PLATFORM_UNAVAILABLE` (retryable; a resubmission is safe because the platform deduplicates
          by `contentSha256`);
        - `REJECTED` / `NOT_FOUND` → `IMPORT_REJECTED`;
        - `CONFLICT` → `IMPORT_SHRINK`;
        - `PROTOCOL` → `PROTOCOL_ERROR`.
     2. Save `step.withExternalRef(jobId)`, then call `run.startImport(jobId)`.
     3. Poll every `polls.importJob`. `NOT_FOUND` → `IMPORT_JOB_LOST`. Once the run is `IMPORTING`, the IMPORT step is
        never retried with a new attempt; it follows the bound job until the job ends or the step fails.
     4. When the job finishes, build the `ImportReport` with `ImportReportMapper`, unless one already exists for the
        run:
        - counters are summed over the seasons that have a result;
        - `issues` are, in order, the job `errorDetail`, then each season's `errorDetail` as `<season>: <detail>`,
          then each season's `executionIssues` as `<season>: <issue>`;
        - `importStatus` is the job status, `rawReport` is the job JSON as received, and `receivedAt` is now.
     5. Finish according to the job status:
        - `SUCCEEDED` → step succeeded, run `succeed()`;
        - `PARTIAL` → step succeeded with outcome `PARTIAL`, run `partial()`;
        - `FAILED` → step failed with `IMPORT_FAILED` (message: the job `errorDetail`, or "Import job <id> failed"),
          run `fail`.
   - **Resuming an active run** (after a restart, or a dispatch of a run that is already in progress):
     - `QUEUED`:
       - a `RUNNING` INGEST step is failed as `INTERRUPTED` (`retryable=true`), then the INGEST rules apply.
     - `RUNNING_INGEST`:
       - if the latest INGEST step is `RUNNING` with a ref, keep polling `run.ingestRunId()`;
       - if it is `RUNNING` without a ref, fail it as `INTERRUPTED` (retryable) and continue;
       - if it has already finished (a crash before the run was updated), derive the run transition from the step:
         `NO_CHANGES` → `noChanges`, any other success → `packed`, a failure → the retry rule.
     - `PACKED`:
       - with a stored ZIP artifact: a `RUNNING` IMPORT step that has a ref calls `startImport(ref)` and is polled;
         one without a ref is failed as `INTERRUPTED` (retryable) and resubmitted;
       - without an artifact: a `RUNNING` FETCH_PACKAGE step is failed as `INTERRUPTED` (retryable) and fetched again.
     - `IMPORTING`: poll `run.importJobId()`.
   - **Interruption and unexpected errors.**
     - `InterruptedException` (shutdown) restores the interrupt flag and returns. The run and step stay as they are,
       and recovery resumes them.
     - `StaleRunException` means another executor owns the run. Log it and return without writing.
     - Any other `RuntimeException` reloads the run and fails it (and a `RUNNING` step) with `INTERNAL_ERROR`. The
       message is `<exception class>: <message>`, truncated to 500 characters. If the run cannot be loaded, the
       exception is rethrown.

5. **Launch and recovery (`<core>/execution/`).**
   - `RunLauncher.launch(LaunchRequest request) -> PipelineRun`:
     - `LaunchRequest` holds `source`, `season`, `scope`, `trigger`, `requestedBy` and `retryOfRunId`.
     - `launch` creates `PipelineRun.queue(UUID.randomUUID(), ..., clock.now())` through `runs.create`.
       `ActiveRunConflictException` propagates unchanged, and FEAT-00105 maps it to 409.
     - It then calls `dispatcher.dispatch(id)`. If dispatch throws, the queued run is failed with `DISPATCH_FAILED`
       and the exception is rethrown.
     - This is the single trigger path that FEAT-00105 (manual) and FEAT-00106 (scheduled) build on.
   - `RunRecovery.recover()`: `runs.findByStatusIn(RunStatus.active())`, dispatched oldest first.

6. **Core tests (JUnit 5 + AssertJ, no threads, no real waiting).**
   - Fixtures in `src/test/java/.../core/execution/testing/`:
     - `InMemoryPipelineRunRepository`, which enforces one active run per source and the version and
       `StaleRunException` rules;
     - `InMemoryPipelineStepRepository`, `InMemoryRunArtifactRepository`, `InMemoryImportReportRepository`;
     - `InMemoryArtifactStore`, which hashes for real;
     - `ScriptedIngestGateway` and `ScriptedImportGateway`, which queue responses or exceptions per call and record
       calls;
     - `FakeRunClock`, where `sleep` advances `now` and records the duration;
     - `RecordingDispatcher` and `RecordingObserver`.
   - Publish these fixtures as the core `test-jar`: add a `maven-jar-plugin` `test-jar` execution to the core POM
     (a build plugin, not a dependency, so `CoreDependencyRulesTest` still passes). The runtime tests in step 10 reuse
     them.
   - `RetryPolicyTest` covers the delay sequence, the cap, exhaustion and validation. `StepTimeoutsTest` and
     `PollIntervalsTest` cover validation.
   - `RunExecutorTest`:
     - full success: step rows are INGEST/1, FETCH_PACKAGE/1 and IMPORT/1; the run is `SUCCEEDED` with `ingestRunId`
       and `importJobId`; the artifact and the report counters are summed over two seasons;
     - `NO_CHANGES`, with no fetch or import call;
     - `COMPLETED_WITH_ISSUES` gives `PACKED`;
     - a package-less success gives `INGEST_NO_PACKAGE`;
     - `SOURCE_UNAVAILABLE` twice then success: three attempts, recorded sleeps of 30 s and 60 s, and `ingestRunId`
       equal to the third ingest id;
     - `SOURCE_UNAVAILABLE` four times: `FAILED` after 4 attempts with sleeps of 30/60/120 s;
     - ingest `POST` 503 then success;
     - `409` gives `INGEST_BUSY` with no retry;
     - ingest `FAILED` with no retry;
     - poll 404 gives `INGEST_RUN_LOST`, then a new attempt;
     - three failed polls then success, all in one attempt; four consecutive failed polls fail the attempt (retryable);
     - an ingest that stays `RUNNING` past its timeout gives `STEP_TIMEOUT`, and the last sleep is clipped to the
       deadline;
     - a back-off that would cross the deadline is not taken;
     - package 503 then success;
     - package 404 gives `PACKAGE_GONE`;
     - checksum mismatch gives `PACKAGE_CHECKSUM_MISMATCH` and the file is deleted;
     - import `POST` 503 then a `200` deduplicated job;
     - import 400 gives `IMPORT_REJECTED`, and 409 gives `IMPORT_SHRINK`;
     - a `PARTIAL` job gives a `PARTIAL` run;
     - a `FAILED` job gives a `FAILED` run with the report stored and `IMPORT_FAILED`;
     - a job 404 gives `IMPORT_JOB_LOST`;
     - an import that stays `IMPORTING` past its timeout gives `STEP_TIMEOUT`;
     - an unexpected exception from a gateway gives `INTERNAL_ERROR`;
     - `StaleRunException` stops without writing;
     - interruption leaves the run active;
     - the observer sees every run and step change in order.
   - `RunExecutorResumeTest` covers each resume rule in step 4.
   - `ImportReportMapperTest` covers summing, issue order and a season without a result.
   - `RunLauncherTest` covers queue then dispatch, conflict propagation and dispatch failure. `RunRecoveryTest`
     checks oldest-first order and that only active runs are dispatched.
   - `CoreDependencyRulesTest` stays unchanged and green.

7. **Runtime configuration.**
   - Extend `<rt>/config/PipelineOrchestratorProperties`. All `Duration`s must be positive; invalid values fail in the
     compact constructors, which makes binding fail at startup.
     - `platform`:
       - `apiKey` (`@NotBlank`; the raw service-credential key from FEAT-00101, which needs `imports:write`);
       - `connectTimeout`, `readTimeout`, `pollInterval`.
     - `ingest`: `connectTimeout`, `readTimeout`, `pollInterval`.
     - `artifacts`: `@NotNull Path dir`.
     - `execution`:
       - `maxRetries`, `initialBackoff`, `backoffMultiplier`, `maxBackoff`;
       - `timeouts.ingest`, `timeouts.fetchPackage`, `timeouts.importJob`;
       - `maxConcurrentRuns` (1..3);
       - `recoverOnStartup`;
       - `toSettings()` builds the core `ExecutionSettings`.
   - Changes to `src/main/resources/application.yml`:
     - New required variables without defaults: `PIPELINE_PLATFORM_API_KEY` and `PIPELINE_ARTIFACTS_DIR`.
     - Tuning values with documented defaults that the environment can override:
       - `PIPELINE_INGEST_POLL_INTERVAL:PT15S`, `PIPELINE_IMPORT_POLL_INTERVAL:PT10S`;
       - connect timeouts `PT10S`; read timeouts `PT1M` (ingest) and `PT5M` (platform, which validates the upload
         synchronously);
       - `PIPELINE_MAX_RETRIES:3`, `PIPELINE_INITIAL_BACKOFF:PT30S`, `PIPELINE_BACKOFF_MULTIPLIER:2`,
         `PIPELINE_MAX_BACKOFF:PT5M`;
       - `PIPELINE_INGEST_TIMEOUT:PT3H`, `PIPELINE_FETCH_PACKAGE_TIMEOUT:PT10M`, `PIPELINE_IMPORT_TIMEOUT:PT3H` (the
         import timeout exceeds the platform's default `PT2H` busy wait);
       - `PIPELINE_MAX_CONCURRENT_RUNS:3`, `PIPELINE_RECOVER_ON_STARTUP:true`.
     - **Restore the no-default contract** that FEAT-00103 broke. Remove the defaults of `PIPELINE_DB_URL`,
       `PIPELINE_DB_USERNAME`, `PIPELINE_DB_PASSWORD` (a committed password `admin`), `PIPELINE_PLATFORM_URL` and
       `PIPELINE_INGEST_URL`. Both URLs currently default to a JDBC URL, which is not an HTTP base URL. This matches the
       runtime `AGENTS.md` and README, which already say these variables have no default.

8. **HTTP gateways (`<rt>/gateway/`).**
   - `HttpClientsConfiguration` builds two `RestClient`s (`ingestRestClient`, `platformRestClient`) from Boot's
     `RestClient.Builder`:
     - the base URL, and a default `X-API-Key` header with the ingest key or the platform service key;
     - a `JdkClientHttpRequestFactory` over an `HttpClient` with the connect timeout, plus the read timeout.
     - Keys are never logged.
   - `GatewayErrors.translate(...)` maps:
     - `ResourceAccessException` (I/O, connect and read timeouts) and 5xx → `UNAVAILABLE`;
     - 404 → `NOT_FOUND`; 409 → `CONFLICT`;
     - any other 4xx → `REJECTED` (401/403 say "check the configured API key", without echoing it);
     - an unreadable or unexpected body → `PROTOCOL`.
     - The message is the method, path, status and at most 300 characters of the body's `detail`/`message` field.
   - `IngestServiceJobRunner implements IngestGateway`. The class takes the name the acceptance criteria use;
     `IngestGateway` is the `JobRunner` interface of the guidelines.
     - `POST /api/v1/ingest/runs` with `{source, season, stages: ["download","parse","package"], mode}`.
       - `mode` is `"snapshot"` or `"delta"`, with `force=false` and `allowPublishedShrink=false`.
       - `scopes` is added only for a scoped run, using the FEAT-00098 keys with nulls and an empty `matchDays`
         omitted, and never together with `filters`.
       - Ingest accepts upper-case source names, so `PipelineSource.name()` is sent as is.
       - Body records are private to the adapter (the ingest contract is not the persisted scope JSON).
     - The `runId` response must be non-blank and at most 64 characters, otherwise `PROTOCOL`.
     - `GET /runs/{id}` uses a DTO with `@JsonIgnoreProperties(ignoreUnknown = true)`; a non-null `package` means
       `packageAvailable`.
     - `GET /runs/{id}/package` uses `exchange` to stream the body into the sink without buffering.
       `X-Content-SHA256` must match `[0-9a-f]{64}`, otherwise `PROTOCOL`, and the sink is not called.
   - `HttpImportGateway`:
     - `POST /api/v1/administration/import/jobs` sends multipart `file` (an `AbstractResource` over `ArtifactContent`
       with `contentLength()` and `getFilename()`), `runId` and `allowPublishedShrink=false`. A 202 or 200 response
       maps to `ImportSubmission`.
     - `GET /jobs/{id}` reads the body as a `String`, which becomes `rawJson`, then parses it with the Boot
       `ObjectMapper` into adapter DTOs that ignore unknown fields. Missing counters map to 0, and a season with
       `result: null` has no counters.
   - `<rt>/artifact/FileSystemArtifactStore`:
     - At construction the root must exist, be a directory and be writable; otherwise it throws
       `IllegalStateException` naming the path, so startup fails. Leftover files in `<root>/.tmp/` are deleted.
     - Keys resolve under the root with `normalize()` and a `startsWith(root)` check.
     - `store` writes `<root>/.tmp/<uuid>.part` through a `DigestInputStream`, forces it to disk, then moves it with
       `ATOMIC_MOVE` after creating the parent directories. An existing target raises `ArtifactStoreException`, and a
       failure deletes the part file.
   - `<rt>/execution/SystemRunClock` uses `Instant.now()` and `Thread.sleep`. `LoggingRunObserver` writes one INFO line
     per run or step change with the run id, status, step kind/attempt and error code.

9. **Dispatch and wiring (`<rt>/execution/`).**
   - `ExecutorRunDispatcher implements RunDispatcher`:
     - It owns a **private** fixed pool of `maxConcurrentRuns` platform threads named `pipeline-run-N`. It is not
       exposed as an `Executor`/`TaskExecutor` bean: Boot's `applicationTaskExecutor` backs off when one exists, and
       FEAT-00105's SSE will need it.
     - A concurrent set of in-flight run ids ignores a second dispatch of a run that is already executing.
     - Each task calls `RunExecutor.execute` and logs any escaped exception with the run id.
     - `@PreDestroy` calls `shutdownNow()` and waits up to 30 s. Interrupted runs stay active.
   - `RunExecutionConfiguration` declares beans for the gateways, `FileSystemArtifactStore`, `SystemRunClock`,
     `LoggingRunObserver`, `ExecutionSettings`, `RunExecutor`, `RunLauncher` and `RunRecovery`. An
     `ApplicationReadyEvent` listener calls `RunRecovery.recover()` when `recoverOnStartup` is true. A run created by
     FEAT-00105 in the same window is protected by the in-flight set and by the optimistic version.
   - No controller, scheduler or security change (FEAT-00105/106).

10. **Runtime tests.**
    - `src/test/java/<rt>/gateway/StubHttpServer`: JDK `com.sun.net.httpserver.HttpServer` on `127.0.0.1:0`, so no
      new dependency and no network.
      - Handlers are scripted per method and path, as a queue of responses with status, headers, body and an optional
        delay.
      - It records requests (method, path, headers, body bytes).
      - `close()` stops it, which lets tests simulate connection refused.
    - Add the core `test-jar` as a test-scoped dependency (`<type>test-jar</type>`, version `${project.version}`).
    - `IngestServiceJobRunnerTest`:
      - exact JSON for full-season and scoped requests, and the `X-API-Key` header;
      - state mapping for running, each outcome and the `package` null/present cases;
      - 400/409/422/404/500 mapping, connection refused and read timeout mapped to `UNAVAILABLE`;
      - streaming package download with the header, and a missing or upper-case header mapped to `PROTOCOL`.
    - `HttpImportGatewayTest`:
      - multipart parts (`file` name and bytes, `runId`, `allowPublishedShrink=false`) and the header;
      - 202 (`created=true`) and 200 (`created=false`);
      - 400/409/401/500 mapping;
      - a two-season job with one `result: null`, unknown fields ignored and `rawJson` byte-identical.
    - `FileSystemArtifactStoreTest` (`@TempDir`): hash and size, no visible file after a failing stream, existing key
      rejected, `..` or absolute key rejected, missing or non-directory root fails, `.tmp` cleanup.
    - `RunExecutorHttpTest` covers the acceptance paths end to end over HTTP. It uses the real gateways and
      `FileSystemArtifactStore` against two `StubHttpServer`s, the core in-memory repositories and `FakeRunClock`.
      - success: ZIP stored with the matching SHA-256 and an `import_report` with the counters;
      - no change;
      - retry: ingest 503 then success, and `SOURCE_UNAVAILABLE` then success;
      - timeout: ingest still `RUNNING` at the deadline, and a stub delay longer than the read timeout counted as
        `UNAVAILABLE`;
      - import failure: job `FAILED`, and 409 shrink;
      - ingest run lost (404) followed by a new ingest run.
    - `ExecutorRunDispatcherTest`: named threads, duplicate dispatch ignored, at most `maxConcurrentRuns` at once,
      shutdown interrupts a sleeping run without failing it.
    - `PipelineOrchestratorPropertiesTest`:
      - a missing `platform.api-key` or `artifacts.dir` fails startup;
      - a zero timeout or poll interval, negative retries, a multiplier below 1, `maxBackoff < initialBackoff` and
        `maxConcurrentRuns` 0 or 4 each fail.
    - `RunExecutionPersistenceTest` (`@PipelinePersistenceTest`, skipped without Docker): the success path with the
      JPA repositories and stub servers (step update through `withExternalRef`, version increments, artifact and
      report rows), and recovery of a stored `IMPORTING` run that resumes polling.
    - `@PipelinePersistenceTest` and `PipelineOrchestratorApplicationTest`:
      - add `platform.api-key`, plus `artifacts.dir` pointing to a temp directory created by the test configuration;
      - set `tt.pipeline.execution.recover-on-startup=false`, so a cached context never dispatches rows left by
        another test class.

11. **Documentation and guidance.**
    - `tt-league-pipeline-orchestrator-runtime/README.md`:
      - the new variables in the configuration table, including that `PIPELINE_PLATFORM_API_KEY` is a FEAT-00101
        service credential with `imports:write`;
      - a "Run execution" section covering the flow, the outcome table, the retry, timeout and poll rules, the failure
        codes, the artifact layout `<dir>/<source>/<season>/<runId>/ingest-<ingestRunId>.zip`, recovery after restart,
        and the fact that ingest cannot cancel a timed-out run.
    - `tt-league-pipeline-orchestrator-runtime/docs/pipeline-datamodel.md`: no migration. Add a "Written by the run
      executor" section:
      - step kinds and attempt numbering, the `external_ref` contents and the `outcome` values;
      - `error_code` values (`FailureCode`);
      - the `storage_key` layout;
      - how `import_report` is derived from the platform job;
      - `ingest_run_id` holds the ingest run currently followed.
    - Core `AGENTS.md` and README: describe the `execution` package (ports, `RunExecutor`, launcher, recovery,
      `System.Logger` only) and the published test fixtures.
    - Runtime `AGENTS.md`:
      - HTTP calls go only through the gateways;
      - never log keys;
      - the run dispatcher must never be an `Executor` bean;
      - required variables keep no defaults.

12. **Validation.**
    - Run `mvn -pl tt-league-pipeline-orchestrator-core,tt-league-pipeline-orchestrator-runtime -am test`, then the
      full `mvn test`. If the reactor cannot resolve the core `test-jar` during `test`, fall back to copying the
      fixtures into the runtime tests and record it.
    - Run the persistence tests with Docker before `in-review`, or record that they were skipped.
    - Review the diff for `target/` content, keys, and any `tt-data-league-*` import.

## Acceptance Criteria

- [x] `IngestServiceJobRunner` starts a `tt-league-ingest-rest` run (`download`, `parse`, `package`) for the run's source and scope, polls it and maps its `outcome` to `NO_CHANGES`, `PACKED` or `FAILED`
- [x] On `PACKED`, the ZIP is fetched, its SHA-256 verified and stored in a configured artifact directory (`run_artifact` row), then submitted to the platform import jobs API
- [x] The import job is polled to completion; its counters are stored in `import_report` and the run ends `SUCCEEDED`, `PARTIAL` or `FAILED`
- [x] Every step has a configurable timeout; `SOURCE_UNAVAILABLE` and HTTP 5xx are retried up to 3 times with exponential back-off, other failures are not
- [x] An ingest run that disappears (ingest restarted, 404) fails the step with a clear, retryable error
- [x] Tests use stubbed HTTP servers (no network) for success, no-change, retry, timeout and import-failure paths

# Implementation Guidelines

- The ingest service runs one ingestion at a time across sources; account for its queue in step timeouts.
- Docker/Kubernetes job runners are out of scope (D4). Keep `JobRunner` an interface so they can be added.
- 2026-10-04: with the single-VM Docker Compose deployment (D9) the ingest-REST runner is the only planned runner;
  orchestrator and ingest reach each other by Compose service name, configured explicitly.
- Core stays framework-free. The new `execution` package uses only JDK types: `InputStream`, `Duration`, and
  `System.Logger` for logging. Time and sleeping go through the `RunClock` port, so tests never wait. Model
  transitions still take explicit `Instant`s, as FEAT-00103 decided.
- The orchestrator integrates over HTTP only. It never reads platform tables or ingest folders, and never adds a
  `tt-data-league-*` dependency.
- Never log or echo `X-API-Key` values, and never put them in `RunError` messages.
- Do not add a retry for anything that the retry rule in step 4 does not list. In particular, a timeout,
  `INGEST_BUSY`, `IMPORT_SHRINK` or `IMPORT_FAILED` is final.
- The database stays the guarantee for one active run per source. The dispatcher's in-flight set only avoids
  executing the same run twice in one JVM.
- Out of scope:
  - the runs API, SSE and security (FEAT-00105);
  - the scheduler (FEAT-00106);
  - tracker recompute (FEAT-00107; it hooks into `RunObserver`);
  - replay and artifact retention (FEAT-00114);
  - metrics and ingest log correlation (FEAT-00115);
  - cancelling an ingest run (ingest has no cancel API).

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Job runner", "Importer client" and "Concurrency and safety". Decision D5: the orchestrator, not ingest, uploads the ZIP.

## Planning notes (2026-10-04)

- **Contracts were checked against the code.** These are `ingest_rest/app.py` (FEAT-00097/98/99) and
  `ImportJobController` / `ImportJobDto` (FEAT-00100/101). The plan uses only fields these contracts already expose,
  so no ingest, platform or Flyway change is needed.
- **Naming.** The acceptance criteria name `IngestServiceJobRunner`. That class is the runtime adapter, and it
  implements the core port `IngestGateway`, the name in the core `AGENTS.md` and decision D3. The port is the
  "`JobRunner` interface" of the guidelines.
- **"Retried up to 3 times"** is read as 3 retries, so at most 4 attempts per step kind. It is configurable through
  `PIPELINE_MAX_RETRIES`. Change the default to 2 if the intent was 3 attempts in total.
- **Two retry levels.** A start call (ingest run, package download, import submission) that fails with 5xx or I/O
  becomes a new `pipeline_step` attempt. A poll that fails with 5xx or I/O is repeated inside the same attempt with
  back-off, and more than `maxRetries` consecutive failures fail the attempt. Both levels use one `RetryPolicy`.
- **The lost ingest run (404) is retried automatically** within the same budget. The acceptance criterion calls it
  retryable, and after an ingest restart a new ingest run is the right recovery. Confirm this, or restrict automatic
  retries to `SOURCE_UNAVAILABLE` and 5xx only.
- **Model additions.** `PipelineRun.restartIngest` keeps `ingest_run_id` pointing at the ingest run being followed
  after a retry. FEAT-00103 has no self-transitions, so this is a field update in `RUNNING_INGEST`, not a status
  change. `PipelineStep.withExternalRef` lets a step row exist before its external id is known.
- **Import resubmission is safe.** FEAT-00099/00100 deduplicate by the platform-verified `contentSha256`, so a
  submission retried after a lost 5xx response returns the existing job (`200`, `created=false`). Once the run is
  `IMPORTING`, the job id is bound and is only polled.
- **Resume, not fail, after an orchestrator restart.** Platform jobs are persisted, so `IMPORTING` runs can always be
  resumed. `RUNNING_INGEST` runs resume while ingest still knows the run; otherwise the 404 leads to a new attempt.
- **Known edge cases (documented, not solved):**
  - Ingest has no cancel API, so an ingest run that timed out keeps running. The next run for that source can then
    get `409` from ingest (`INGEST_BUSY`).
  - A crash between the ingest `POST` and saving its id leaves an orphan ingest run with the same effect.
- **RFETM teams.** The orchestrator requests `download`, `parse` and `package` as the acceptance criteria say, not
  `teams`. `equipos-json` is packaged when it already exists. Open question: should RFETM runs also request `teams`?
- **Run id correlation.** The orchestrator run id goes to the platform job's `runId`, and the job's `manifestRunId`
  is the ingest run id. Ingest has no request field for an external run id, so that part of FEAT-00115 stays open.
- **Hooks for later features.** `RunLauncher` is the shared trigger path for FEAT-00105 (manual) and FEAT-00106
  (scheduled). `RunObserver` feeds FEAT-00105 SSE and the FEAT-00107 recompute after a final state.
- **FEAT-00103 configuration regression.** Commit `10cbdad` gave defaults to `PIPELINE_DB_*` (including the password
  `admin`), `PIPELINE_PLATFORM_URL` and `PIPELINE_INGEST_URL`; both URLs default to a JDBC URL. This contradicts the
  runtime `AGENTS.md`, its README and the root rule against secrets. Step 7 removes those defaults, because this
  feature edits the same file and the HTTP clients depend on those URLs.
- **Test fixtures.** The core in-memory repositories and scripted gateways are shared through a core `test-jar`, so
  the runtime HTTP tests do not duplicate them. Step 12 has the fallback if the reactor cannot resolve the
  `test-jar` during `mvn test`.
- **Docker.** As in FEAT-00103, `RunExecutionPersistenceTest` is skipped without Docker. All acceptance paths are
  covered without Docker by `RunExecutorHttpTest`, which uses in-memory repositories over real HTTP.

## Status notes

- 2026-10-04: plan approved by the user and marked `ready`, including the planning decisions above (3 retries / 4
  attempts, automatic retry of a lost ingest run, no `teams` stage for RFETM, removal of the FEAT-00103 configuration
  defaults). The open questions remain and do not block implementation.

- 2026-10-04: implemented. Validation: `mvn -pl tt-league-pipeline-orchestrator-core,tt-league-pipeline-orchestrator-runtime -am test` passes (core 170 tests, runtime 91 with 28 Docker-dependent persistence tests skipped, so `RunExecutionPersistenceTest` and `PipelineOrchestratorApplicationTest` were NOT run; run them with Docker before `done`). The reactor resolves the core `test-jar` during `test`, so no fixture copy was needed. Full `mvn test` stops at `tt-data-league-import` on `BcnesaImportProcessorsTest` (missing fixture `acta_bcnesa_2026_published.json`, unrelated to this feature); with `-Dmaven.test.failure.ignore=true` every other module passes.
- 2026-10-04: deviations and findings. `ExecutionSettings` is built by `PipelineOrchestratorProperties.executionSettings()` (poll intervals live under `platform`/`ingest`). `FileSystemArtifactStore` serializes an existence check before `ATOMIC_MOVE`, because the move silently replaces an existing target. `RunExecutionConfigurationTest` covers the Spring wiring without Docker. Persistence tests set dummy `spring.datasource.*` values only to satisfy the now default-less placeholders; Testcontainers overrides them. A network failure in the middle of a package download surfaces as `ARTIFACT_STORE_FAILED` (final) rather than `PACKAGE_UNAVAILABLE`.
