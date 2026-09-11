# Build Plan

1. **Confirm the current import contract and define the asynchronous run model.**
   - Reuse `ImportExecutionService`, `ImportExecutionRequest`, the existing
     `ImportExecutionResult` metrics, `ImportProcessStatus`, and
     `ImportResource` state transitions introduced by FEAT-00031 rather than
     creating a second import pipeline.
   - Add a run identifier and an immutable status snapshot with `QUEUED`,
     `RUNNING`, `SUCCESS`, `EMPTY_RESULT`, and `FAILURE` states. The snapshot
     must include the stored import-resource id, source/season, percentage
     (nullable when a total cannot be determined), processed/total counts,
     skipped and error counts, safe error details, and the final execution
     result.
   - Keep the first transport polling-based and put the registry behind a
     domain/application port so persistence or SSE/WebSocket delivery can be
     added later without changing the REST or React contract. Use an
     application-runtime in-memory implementation initially; do not add schema
     changes unless restart durability is explicitly required.

2. **Add progress reporting to the shared import executor.**
   - Extend the execution contract with a progress listener/sink that is
     optional for existing CLI callers and receives monotonic snapshots.
   - Thread the sink through `NavigatorImportExecutionService` and the RFETM,
     BCNESA, and FCTT navigator traversal paths. Report file/fixture and
     processor progress using the existing traversal counters; use an
     indeterminate percentage when a reliable total is unavailable rather than
     fabricating precision.
   - Preserve FEAT-00031 source dispatch, processor ordering, run-scoped caches,
     consolidation ordering, failure isolation, and the existing synchronous
     `execute(...)` behavior for the CLI and direct callers. Progress callbacks
     must never change import success/failure semantics.

3. **Introduce asynchronous submission and status application services.**
   - Add an application port/adapter for creating, updating, and reading run
     snapshots, keyed by run id and import-resource id, with an atomic
     active-run check. The check must prevent two accepted runs for the same
     active resource even when requests arrive concurrently.
   - Update `StartImportProcessCommandHandler` to keep the existing missing
     resource and `PROCESSING` guards, mark the resource processing, register a
     queued run, submit the existing process to the configured executor, and
     return an accepted response containing the run id and initial snapshot
     without waiting for traversal.
   - Ensure the background task records `RUNNING`, publishes progress, maps
     `SUCCESS`/`EMPTY_RESULT`/`FAILURE`, calls `finishProcessing(...)`, and saves
     the resource exactly once for terminal completion. Unexpected exceptions
     must become `FAILURE` and must not be reported as success.
   - Add a status query/handler that reads the registry only; it must never
     execute or restart an import. Define not-found and terminal response
     behavior explicitly.

4. **Expose the asynchronous contract through the authenticated REST API.**
   - Change `POST /api/v1/administration/import/start` to return `202 Accepted`
     with the run id and initial status while retaining the
     `importResourceId`-only request shape.
   - Add `GET /api/v1/administration/import/process_status?runId=<uuid>`
     (or the repository's established equivalent) backed by the status query.
     Return normalized state, percentage, processed/total counts, errors, and
     terminal result fields required by the UI.
   - Keep `@PreAuthorize("hasRole('ADMIN')")`, resolve source/season/path only
     from the stored `ImportResource`, and do not accept client-supplied paths
     or resource objects. Preserve the existing source-level `/status`,
     preview routes, and upload behavior.
   - Update controller, API-runtime executor/registry wiring, DTOs, and
     OpenAPI-facing tests. No JPA schema change is planned for the initial
     in-memory run registry.

5. **Implement the polling frontend progress workspace.**
   - Extend `src/api/importJobs.js` with start/status calls and normalize the
     status payload at the API/hook boundary.
   - Add a cancellable, cleanup-safe `useImportProcessStatus` hook under
     `src/hooks`, polling only while a run is queued/running and stopping on
     every terminal state or unmount. Preserve selected resource context and
     prevent stale responses from replacing a newer retry.
   - Update `ImportPanel.jsx` to store the run id separately from the final
     result, submit once, poll the run, and retry the same stored resource.
     Update `ImportProcessWorkspace.jsx` to render queued/running progress,
     percentage, processed/total/error counts, and the existing success,
     empty-result, and failure findings/actions.
   - Add a reusable accessible progress primitive in
     `src/components/ui` only if the existing primitives do not provide one.
     Use `role="progressbar"`, accessible value text, keyboard-accessible
     retry/back controls, and existing CSS/design-contract patterns.
   - Add Catalan, English, and Spanish translations for all states, metrics,
     errors, retry text, and progress accessibility labels. Preserve existing
     upload/source/preview behavior.

6. **Add focused regression and integration coverage.**
   - Import module: progress callback ordering/monotonicity, all three source
     traversals, known-total versus indeterminate progress, and unchanged
     result semantics.
   - Core domain/application: atomic duplicate-run rejection, queued-to-
     terminal transitions, failure/exception mapping, retry after terminal
     failure, resource status persistence, status query read-only behavior,
     and missing run/resource responses.
   - REST/runtime: `202` submission, status route/query mapping, admin
     authorization, server-side resource scoping, executor/registry wiring,
     and preservation of existing routes.
   - Frontend: start-and-poll lifecycle, cleanup/cancellation, stale response
     protection, progressbar accessibility, all terminal views, translated
     copy, and retry retaining the selected resource.

7. **Validate and document the delivered contract.**
   - Run the focused Maven reactors for import, core domain, REST, and API
     runtime, then `mvn test` from the repository root.
   - From `tt-data-league-frontend`, run `npm ci`, `npm run lint`, and
     `npm run build`; run the feature's focused frontend tests when added.
   - Review API response compatibility, authentication/resource scoping,
     thread-pool shutdown behavior, generated files, and the final SDD
     registry/details synchronization before moving the feature to `ready`.

# Implementation Guidelines

- Keep dependency direction unchanged: progress ports and immutable snapshots
  belong in core/application contracts; navigator reporting belongs in
  `tt-data-league-import`; executor and in-memory registry wiring belongs in
  `tt-data-league-api-runtime`; HTTP mapping belongs in
  `tt-data-league-api-rest`; polling and rendering belong in the frontend.
- Reuse the configured Spring executor pattern from the API runtime and ensure
  task submission failures are represented as terminal failures. Do not create
  unmanaged threads or block the request thread.
- Preserve the existing `ImportResource` state machine and FEAT-00031 result
  fields. Do not add broad exception catches, silent fallbacks, unscoped
  resource lookup, or client-controlled filesystem paths.
- The initial progress granularity is file/fixture-level because the current
  navigator summaries expose those counters. Per-record progress,
  cancellation, SSE/WebSocket delivery, and durable run history are follow-up
  enhancements unless implementation evidence requires them.
- “Authenticated resource ownership” means retaining the admin authorization
  boundary and server-side resource-id/path resolution already present. The
  current `ImportResource` model has no owner field; adding user ownership
  persistence requires a separate domain/auth decision and is not assumed by
  this plan.

# Notes
- 2026-09-05: Feature captured as an idea. The asynchronous execution contract,
  progress transport, lifecycle persistence, and UI update strategy remain to be
  designed before planning.
- 2026-09-05: Build plan drafted. Status should move to `planned`; polling and
  an application-runtime in-memory run registry are the initial decisions.
- 2026-09-05: Implementation delivered. Registry moved from `ready` straight to
  `in-review` in this change (build plan and acceptance criteria synchronized
  together with the implementation). Key decisions taken while implementing:
  - Domain/application layer: added `ImportRunStatus`, `ImportRunProgress`
    (nullable total/percentage for indeterminate progress),
    `ImportRunSnapshot` (immutable, state-machine transition methods),
    the `ImportRunRegistry` port, and the `ImportProgressListener` functional
    interface, all under `tt-data-league-core-domain`.
  - `StartImportProcessCommandHandler` now registers a queued run atomically,
    marks the resource processing, submits the actual traversal to the
    injected `java.util.concurrent.Executor` (the existing Spring Boot default
    task executor, same pattern as `ResourceUploadService`), and returns the
    accepted run snapshot immediately. Executor submission failures and
    unexpected exceptions from the traversal are both mapped to a terminal
    `FAILURE` run and an `ERROR` resource status; they are never reported as
    success.
  - `FindImportRunStatusQueryHandler` only reads the registry; it never
    executes or restarts an import.
  - `ImportExecutionService`, the three `*ActasDirectoryNavigator` classes, and
    `NavigatorBackedImportResourceProcessService` gained additive
    `ImportProgressListener` overloads (existing overloads delegate to a no-op
    listener), reporting file/fixture-level progress as an indeterminate
    percentage (no reliable total is available ahead of a traversal), per the
    plan's stated initial granularity.
  - `InMemoryImportRunRegistry` (API runtime) is the initial adapter; runs are
    JVM-lifetime only, and `registerQueued` is atomic via
    `ConcurrentHashMap#putIfAbsent` so two concurrent submissions for the same
    import resource cannot both be accepted.
  - `POST /api/v1/administration/import/start` now returns `202 Accepted` with
    the run id and initial `queued` snapshot on success; existing missing
    resource/already-processing guard responses keep their prior `200` shape.
    `GET /api/v1/administration/import/process_status?runId=<uuid>` polls the
    registry only.
  - Frontend: `useImportProcessStatus` polls every 2s while `queued`/`running`,
    stops on any terminal state, ignores stale responses after a `runId`
    change, and cleans up its timer/abort controller on unmount.
    `ImportProcessWorkspace` renders the queued/running progress bar
    (indeterminate when `percentage` is null) and preserves the existing
    success/empty-result/failure views. Catalan, English, and Spanish
    translations were added for the new states and progress copy.
  - Known blocker (pre-existing, unrelated to this feature): the committed
    `tt-data-league-api-rest` test sources under
    `src/test/java/.../importjob/` (`ImportJobControllerTest`,
    `InMemoryImportJobsServiceTest`) reference main classes that do not exist,
    so `tt-data-league-api-rest` fails `mvn test` test-compile on the
    unmodified baseline. This was verified before this change and left as-is
    per scope; `ImportResourceControllerTest` and the rest of the module's
    tests pass once that unrelated package is excluded from compilation.
  - Also pre-existing and unrelated: 8 `tt-data-league-import` processor tests
    (Bcnesa/Fctt/RFETM club-scoped persistence assertions and
    `TeamToClubConsolidationProcessorTest`) and the `tt-data-league-core-repository-jpa`
    DB-backed tests fail on the same unmodified baseline in this environment;
    confirmed unaffected by this change via a stashed-diff comparison.
- 2026-09-06: User approved the completed implementation; status moved to
  `done`.
