# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>` and `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>`. Test sources mirror them under `src/test/java`. Steps 1-5 are core-only and test with fakes. Steps 6-13 add the
migration, persistence, security, controllers, event stream and documentation. This feature changes no platform
module, platform REST contract or `tt-league-ingest` code: ingest already accepts `force` (FEAT-00097).

**Contracts used (checked against the code on 2026-10-04).**
- Platform JWTs (`tt-data-league-api-rest` `JwtService`): signed with `Keys.hmacShaKeyFor(secret UTF-8 bytes)` and
  `signWith(key)`, so jjwt picks the algorithm from the key length: 32-47 bytes → HS256, 48-63 → HS384, ≥ 64 → HS512.
  Claims: `sub` (username), `roles` (list, for example `ADMIN`), `permissions` (list, for example `matches:write`),
  `jti`, `iat`, `exp` (30 h default). Logout blacklisting lives in platform memory and is not visible here.
- Core trigger path: `RunLauncher.launch(LaunchRequest)` creates a `QUEUED` run (`runs.create` throws
  `ActiveRunConflictException`) and dispatches it. It does not notify `RunObserver` today. `RunExecutor` notifies the
  observer after every `runs.update` / `steps.save`, and an observer exception escapes into `failUnexpected`, so
  observers must never throw into the executor.
- `pipeline_run.scope` is `{"scopes":[...]}` (`RunScopeJson`); the partial unique index
  `ux_pipeline_run_active_source` counts `QUEUED` as active, so a second queued run per source is impossible.
- Ingest start body (`IngestServiceJobRunner.StartBody`) already has `force`, currently hard-coded to `false`.

1. **Run model: `force` (`<core>/run/`, `<core>/execution/`).**
   - `PipelineRun` gains `boolean force`, set by `queue(...)` and `restore(...)` (new parameter after `scope`) and
     carried unchanged by every transition and by `restartIngest`. Add the accessor.
   - `RunLauncher.LaunchRequest` gains `boolean force` (after `scope`) and passes it to `PipelineRun.queue`.
   - `IngestRunRequest` gains `boolean force`; `forRun(run)` copies `run.force()`.
   - Update every caller and fixture: `RunLauncher`, `RunExecutor` (via `IngestRunRequest.forRun`), the core
     `execution/testing` fixtures (`ExecutorHarness`, `InMemoryPipelineRunRepository`), and all tests that call
     `PipelineRun.queue/restore`.
   - Tests: `PipelineRunTest` (force kept through transitions and restore), `IngestRunRequest` mapping covered in
     `RunExecutorTest` (scripted ingest gateway records `force`).

2. **Observer fan-out and launch notification (`<core>/execution/`).**
   - `RunLauncher` takes a `RunObserver` (new constructor parameter) and calls `runChanged(created)` after
     `runs.create`, and `runChanged(failed)` after the `DISPATCH_FAILED` update, so the event stream sees new runs.
   - New `port/CompositeRunObserver implements RunObserver` (`of(List<RunObserver>)`): calls each observer in order;
     a `RuntimeException` from one observer is logged at `WARNING` through `System.Logger` (run id, observer class,
     exception class and message) and does not stop the others or reach the executor. This is the single, documented
     exception to "no broad catches": observers are side channels and must not fail a run.
   - Tests: `RunLauncherTest` (observer sees `QUEUED` on success and `FAILED`/`DISPATCH_FAILED` on dispatch
     failure), `CompositeRunObserverTest` (order, failure isolation, both callbacks).

3. **Run queries (`<core>/run/`, `<core>/run/port/`).**
   - New `RunQuery(Set<PipelineSource> sources, Set<RunStatus> statuses, Instant createdFrom, Instant createdTo,
     int page, int size)`: empty sets mean "any"; `createdFrom` inclusive, `createdTo` exclusive, both optional;
     `page >= 0`; `1 <= size <= 100`; `createdFrom` after `createdTo` is rejected (`IllegalArgumentException`).
   - New `RunPage(List<PipelineRun> items, int page, int size, long totalItems)` with `totalPages()`.
   - `PipelineRunRepository.find(RunQuery)` returns a `RunPage`, newest first (`createdAt` desc, then `id`).
   - `PipelineStepRepository.findByRunIds(Collection<UUID>)` returns `Map<UUID, List<PipelineStep>>` with the same
     per-run order as `findByRunId` (used by the list endpoint to avoid one query per run).
   - Fixtures: implement both in `InMemoryPipelineRunRepository` / `InMemoryPipelineStepRepository`.
   - Tests: `RunQueryTest` (validation), in-memory behaviour covered by `TriggerRunTest` and a small
     `InMemoryPipelineRunRepository` query test.

4. **Pending triggers and the open-match-day scope port (`<core>/trigger/`, new package).**
   - `ScopeType` enum: `OPEN_MATCH_DAYS`, `GROUP`, `FULL_SEASON`. `ConflictMode` enum: `REJECT`, `QUEUE`.
   - `PendingTrigger(PipelineSource source, String season, ScopeType scopeType, List<ScopeFilter> filters,
     boolean force, String requestedBy, Instant requestedAt)` — validates the same rules as the trigger command
     (step 5) so a stored row is always launchable.
   - `port/PendingTriggerRepository`: `PendingTrigger add(PendingTrigger)` (throws
     `PendingTriggerExistsException(source)` when one is already stored), `Optional<PendingTrigger> take(source)`
     (atomic find-and-delete), `List<PendingTrigger> findAll()` (by `requestedAt`).
   - `port/OpenMatchDayScopeResolver`: `RunScope resolve(PipelineSource source, String season)` returning a scope with
     at least one filter, or throwing `ScopeUnavailableException(code, message)` (codes: `SCOPE_UNAVAILABLE` when no
     resolver exists yet, `NO_OPEN_MATCH_DAYS` for FEAT-00108's empty case). Messages are user-facing and contain
     no secrets.
   - `port/PendingTriggerEvents`: `queued(PendingTrigger)`, `launched(PendingTrigger, PipelineRun)`,
     `dropped(PendingTrigger, String code)` as default no-op methods plus `none()` (same shape as `RunObserver`);
     the runtime event stream implements it (step 12).
   - Fixtures in `execution/testing` (published test-jar): `InMemoryPendingTriggerRepository`,
     `StubOpenMatchDayScopeResolver` (scripted scope or exception), `RecordingPendingTriggerEvents`.

5. **`TriggerRun` use case (`<core>/trigger/TriggerRun.java`) — the one path for manual and scheduled runs.**
   - Constructor: `PipelineRunRepository`, `PendingTriggerRepository`, `OpenMatchDayScopeResolver`, `RunLauncher`,
     `PendingTriggerEvents`, `RunClock`. Every queue, launch and drop of a pending trigger notifies
     `PendingTriggerEvents` (isolated like `CompositeRunObserver`: a listener failure is logged, never rethrown).
   - `Command(List<PipelineSource> sources, String season, ScopeType scopeType, List<ScopeFilter> filters,
     boolean force, RunTrigger trigger, String requestedBy, ConflictMode conflictMode)`. Validation
     (`IllegalArgumentException`, mapped to 400): sources non-empty and distinct; season matches the `PipelineRun`
     rule (validated up front, not per source); `GROUP` requires at least one filter and exactly one source;
     `FULL_SEASON` and `OPEN_MATCH_DAYS` reject filters; trigger is `MANUAL` or `SCHEDULED` (`RETRY` is not created
     here); `requestedBy` non-blank, at most 128 characters.
   - `List<Outcome> trigger(Command)`: sources are processed independently in `PipelineSource` order (no
     all-or-nothing). Sealed `Outcome` with `source()`:
     - `Created(PipelineRun run)`;
     - `Queued(PendingTrigger pending, UUID activeRunId)`;
     - `Rejected(String code, String message, UUID activeRunId)` with codes `ACTIVE_RUN` and `PENDING_EXISTS`;
     - `Unavailable(String code, String message)` from `ScopeUnavailableException`.
   - Per source: resolve the scope (`FULL_SEASON` → `RunScope.fullSeason()`, `GROUP` → `new RunScope(filters)`,
     `OPEN_MATCH_DAYS` → resolver), then `launcher.launch(...)`. On `ActiveRunConflictException`:
     - `REJECT` → `Rejected(ACTIVE_RUN, "Source <S> already has an active run <id> (<status>)", id)`;
     - `QUEUE` → `pendingTriggers.add(...)` → `Queued`; `PendingTriggerExistsException` → `Rejected(PENDING_EXISTS,
       ...)`. After storing, if `runs.findActiveBySource` is now empty (the active run ended between the conflict
       and the insert), call `launchPending(source)` immediately so the request is never stranded.
     - The pending trigger stores the request (scope type and filters), not the resolved scope:
       `OPEN_MATCH_DAYS` is resolved when it launches.
   - `Optional<PipelineRun> launchPending(PipelineSource source)`: `take(source)`; if present, resolve and launch it
     with trigger `MANUAL` and the stored `requestedBy`/`force`. On `ActiveRunConflictException` (a scheduler or user
     won the race) put it back with `add` and return empty. On `ScopeUnavailableException` drop it and log `WARNING`
     (source, code, requester). Other exceptions propagate.
   - `int drainIdle()`: calls `launchPending` for every stored source without an active run; used at startup.
   - `PendingTriggerDrainer implements RunObserver` (`<core>/trigger/`): on `runChanged` with a terminal status calls
     `launchPending(run.source())`. It receives a `Supplier<TriggerRun>` so the runtime can break the bean cycle
     `TriggerRun → RunLauncher → RunObserver → drainer → TriggerRun`.
   - Tests (`TriggerRunTest`, `PendingTriggerDrainerTest`, using `ExecutorHarness`-style in-memory fixtures and
     `RecordingDispatcher`): single-source `MANUAL` run records `requestedBy` and `force`; `ALL` → one run per
     source; `ALL` with one active source → mixed `Created`/`Rejected`; every validation rule; `OPEN_MATCH_DAYS`
     resolved scope and `Unavailable`; `REJECT` conflict carries the active run id; `QUEUE` stores the pending
     trigger; second pending → `PENDING_EXISTS`; race (active run ended before insert) launches at once;
     `launchPending` launches and removes; conflict puts it back; scope failure drops it; drainer ignores
     non-terminal changes; `drainIdle` skips sources with an active run. `CoreDependencyRulesTest` must stay green.

6. **Flyway `V2__manual_triggers.sql` (`tt-league-pipeline-orchestrator-runtime/src/main/resources/db/migration/`).**
   - `ALTER TABLE pipeline.pipeline_run ADD COLUMN force boolean NOT NULL DEFAULT false;`
   - `CREATE INDEX ix_pipeline_run_created ON pipeline.pipeline_run (created_at DESC, id);` for the unfiltered list.
   - `CREATE TABLE pipeline.pending_trigger (source varchar(16) PRIMARY KEY CHECK (source IN ('RFETM','BCNESA','FCTT')),
     season varchar(9) NOT NULL CHECK (season ~ '^[0-9]{4}-[0-9]{4}$'), scope_type varchar(16) NOT NULL CHECK
     (scope_type IN ('OPEN_MATCH_DAYS','GROUP','FULL_SEASON')), filters jsonb NOT NULL, force boolean NOT NULL,
     requested_by varchar(128) NOT NULL, requested_at timestamptz NOT NULL);` — the primary key enforces one per
     source. `filters` uses the `RunScopeJson` filter layout (`{"scopes":[...]}`).
   - Update `docs/pipeline-datamodel.md`: `force` column, new `pending_trigger` section (written by `TriggerRun`,
     deleted when launched or dropped), new index, `V2` row in the migration history.
   - Test: extend `PipelineSchemaMigrationTest` (column default, table, PK and checks).

7. **Persistence adapters (`<rt>/persistence/`).**
   - `PipelineRunEntity`: `force` column, mapped in both directions.
   - `JpaPipelineRunRepository.find(RunQuery)`: `PipelineRunJpaRepository` also extends
     `JpaSpecificationExecutor<PipelineRunEntity>`; build a `Specification` from the non-empty filters and use
     `PageRequest.of(page, size, Sort.by(DESC, "createdAt").and(Sort.by(DESC, "id")))`.
   - `JpaPipelineStepRepository.findByRunIds` with one `run_id IN (...)` query, grouped in Java.
   - New `PendingTriggerEntity`, `PendingTriggerJpaRepository`, `JpaPendingTriggerRepository`: `add` uses
     `saveAndFlush` on a new entity (`Persistable#isNew` true) and maps the PK violation
     (`DataIntegrityViolationException`) to `PendingTriggerExistsException`; `take` runs `SELECT ... FOR UPDATE`
     (`@Lock(PESSIMISTIC_WRITE)`) then delete in one transaction. Reuse `RunScopeJson` for `filters`.
   - Tests (Testcontainers, `disabledWithoutDocker`): `JpaPipelineRunRepositoryTest` (force round trip; source,
     status and date filters; paging totals; newest-first order), `JpaPipelineStepRepositoryTest`
     (`findByRunIds`), new `JpaPendingTriggerRepositoryTest` (add, duplicate → exception, take removes, take on
     empty, filters round trip).

8. **Ingest `force` (`<rt>/gateway/IngestServiceJobRunner.java`).**
   - `StartBody.from(request)` sends `request.force()` instead of the literal `false`; `allowPublishedShrink`
     stays `false`.
   - Test: `IngestServiceJobRunnerTest` asserts `"force":true` in the recorded start body for a forced run and
     `false` otherwise.

9. **Configuration (`<rt>/config/PipelineOrchestratorProperties.java`, `application.yml`).**
   - `Security` becomes `Security(@NotBlank String jwtSecret, List<String> corsAllowedOrigins)`; the compact
     constructor checks at least 32 UTF-8 bytes (the platform rule) instead of `@Size(min = 32)` characters, and
     each origin is an absolute `http(s)` origin. `cors-allowed-origins: ${PIPELINE_CORS_ALLOWED_ORIGINS:}` — empty
     means no CORS headers (same-origin or reverse proxy, FEAT-00116).
   - New `Triggers(ConflictMode conflictMode)`: `conflict-mode: ${PIPELINE_TRIGGER_CONFLICT_MODE:REJECT}`
     (required after binding; an unknown value fails startup).
   - New `Events(Duration heartbeatInterval, Duration emitterTimeout, Integer maxSubscribers)`:
     `PT15S`, `PT30M`, `50` via `PIPELINE_EVENTS_HEARTBEAT`, `PIPELINE_EVENTS_TIMEOUT`,
     `PIPELINE_EVENTS_MAX_SUBSCRIBERS`; durations positive, subscribers 1-1000.
   - Tests: `PipelineOrchestratorPropertiesTest` for each new rule (short secret by bytes, bad origin, unknown
     conflict mode, non-positive durations, subscriber bounds). Add the new properties to
     `PipelineOrchestratorApplicationTest` only where defaults do not apply.

10. **Security (`<rt>/security/`, new package; `pom.xml`).**
    - Add `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` (versions from the Boot
      BOM; no parent POM change) and `spring-security-test` (test scope).
    - `PlatformJwtDecoderFactory`: builds `NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes, jcaName))
      .macAlgorithm(alg)` with `alg` chosen from the secret length exactly as jjwt does (HS256/HS384/HS512), plus the
      default timestamp validator (expiry, 60 s skew).
    - `PlatformJwtAuthenticationConverter`: principal name = `sub`; authorities = each `permissions` entry as-is
      (`matches:write`) plus each `roles` entry as `ROLE_<role>`. Missing or non-list claims give no authorities.
    - `SecurityConfiguration` (`SecurityFilterChain`): stateless, CSRF disabled (bearer tokens only, no cookies),
      CORS from `corsAllowedOrigins` (methods `GET`, `POST`; header `Authorization`, `Content-Type`,
      `Last-Event-ID`); `permitAll` for `/actuator/health`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`,
      `/error`; `POST /api/pipeline/runs` → `hasAuthority("matches:write")`; every other request authenticated.
      401/403 bodies are `ProblemDetail` JSON (custom entry point and access-denied handler); no token value is
      ever logged or echoed.
    - `CurrentUser` helper reads the authenticated name for `requestedBy`.
    - Tests: `SecurityConfigurationTest` (`@WebMvcTest` slice with the real decoder and a test secret; tokens signed
      in the test with Nimbus using the platform claim layout): 401 without/with malformed/expired/wrong-signature
      token; HS256, HS384 and HS512 secrets accepted; `GET` with any valid token; `POST` without `matches:write` →
      403; `ADMIN` role alone does not grant `matches:write` unless the claim lists it (the platform puts
      permissions in the token); health stays public; CORS preflight allowed only for configured origins.

11. **Runs API (`<rt>/api/`, new package).**
    - `RunQueryService` (runtime, read-only transaction boundary): loads a `RunPage` plus steps by run ids for the
      list, and run + steps + artifacts + import report for the detail. Uses only core ports.
    - DTOs (records): `TriggerRunRequest(@NotBlank String source /* RFETM|BCNESA|FCTT|ALL */, @NotBlank String
      season, @NotNull ScopeType scopeType, List<ScopeFilterDto> filters, boolean force)`;
      `TriggerResponse(List<TriggerResultDto> results)` with `source`, `outcome` (`CREATED`, `QUEUED`, `REJECTED`,
      `UNAVAILABLE`), `code`, `message`, `run` (summary, when created), `activeRunId`;
      `RunSummaryDto` (id, source, season, scope filters, `fullSeason`, trigger, requestedBy, force, status,
      createdAt, startedAt, finishedAt, `durationMs` (finished − started; for active runs now − started using
      `RunClock`; null before start), error {code, message}, ingestRunId, importJobId, retryOfRunId, `steps`: latest
      attempt per kind {kind, status, attempt});
      `RunDetailDto` = summary + all step attempts (kind, attempt, status, startedAt, finishedAt, durationMs,
      externalRef, outcome, retryable, error) + artifacts (kind, sha256, sizeBytes, createdAt; no storage key) +
      `importReport` (status, the ten counters, receivedAt; no raw JSON) + `issues` (import report issues, then the
      run error message when FAILED); `PageDto<T>(items, page, size, totalItems, totalPages)`;
      `PendingTriggerDto`.
    - `RunsController` (`/api/pipeline/runs`):
      - `POST` → builds `TriggerRun.Command` with `sources` = all `PipelineSource` values for `ALL`, trigger
        `MANUAL`, `requestedBy` = JWT subject, `conflictMode` from configuration. Status: `201` (with `Location` of
        the first created run) when any outcome is `Created`; else `202` when any is `Queued`; else `409` when any is
        `Rejected`; else `422`. `409`/`422` are `ProblemDetail` with `code`, a `detail` joining the outcome messages
        and the `results` list as an extension property; `2xx` return `TriggerResponse`.
      - `GET` with `source` (repeatable), `status` (repeatable), `from`, `to` (ISO-8601 instants), `page` (default
        0), `size` (default 20, max 100) → `PageDto<RunSummaryDto>`.
      - `GET /{id}` → `RunDetailDto`, `404` `ProblemDetail` for an unknown id.
    - `PendingTriggersController`: `GET /api/pipeline/pending-triggers` → list (authenticated).
    - `ApiExceptionHandler` (`@RestControllerAdvice`): bean validation, `IllegalArgumentException` from core
      validation, unknown enum values and malformed instants → `400` `ProblemDetail` naming the field; unknown run
      → `404`. No stack traces or internal class names in responses.
    - Wiring in a new `TriggerConfiguration`: `TriggerRun`, `PendingTriggerRepository` adapter,
      `OpenMatchDayScopeResolver` bean `UnavailableOpenMatchDayScopeResolver` (always throws `SCOPE_UNAVAILABLE`:
      "Open match days are not available until the match-day tracker and scope builder are deployed"), declared
      `@ConditionalOnMissingBean` so FEAT-00108 replaces it by adding its own bean.
    - `RunExecutionConfiguration`: the `runObserver` bean becomes `CompositeRunObserver.of(logging, broadcaster,
      drainer)` (`@Primary`; the parts are injected by concrete type, so the `RunObserver` type stays unambiguous);
      the drainer gets `() -> triggerRunProvider.getObject()` from an `ObjectProvider<TriggerRun>`; `RunLauncher`
      receives the observer. After `RunRecovery.recover()` in the `ApplicationReadyEvent` listener, call
      `triggerRun.drainIdle()` and log the count.
    - `springdoc-openapi-starter-webmvc-ui` (managed by the parent POM) with `@Operation`/`@ApiResponse`
      annotations and a bearer security scheme.
    - Tests: `RunsControllerTest` (`@WebMvcTest` + security config, `TriggerRun` and `RunQueryService` mocked):
      every 400 case (missing/malformed season, unknown source or scope type, `GROUP` without filters, `ALL` with
      `GROUP`, filters with `FULL_SEASON`, size > 100, `from` after `to`); 201 with `Location` and
      `requestedBy` = token subject; 202 queued; 409 body with code, message and active run id; mixed `ALL`
      outcomes → 201 with all results; 422 for `OPEN_MATCH_DAYS`; list paging and filters passed through; detail
      JSON shape; 404. `PendingTriggersControllerTest`.

12. **Event stream (`<rt>/events/`, new package).**
    - `RunEventBroadcaster implements RunObserver`: keeps `CopyOnWriteArraySet<SseEmitter>`; `runChanged` publishes
      event `run` (`RunSummaryDto` without `steps`), `stepChanged` publishes `step` (step DTO with `runId`). It also
      implements `PendingTriggerEvents` and publishes `pending-trigger`
      (`{source, state: QUEUED|LAUNCHED|DROPPED, requestedBy, runId?, code?}`).
    - Sends happen on a private single-thread `ExecutorService` owned by the broadcaster (not an `Executor` bean,
      same rule as `ExecutorRunDispatcher`) with a bounded queue (1000); when the queue is full the event is dropped
      and logged once per minute, so a slow client never blocks a run thread. A failed `send` completes and removes
      that emitter. `@PreDestroy` completes all emitters and shuts the pool down.
    - Heartbeat: a comment line (`: keep-alive`) every `heartbeatInterval` from the same private pool
      (`ScheduledExecutorService`).
    - `RunEventsController`: `GET /api/pipeline/events` (`text/event-stream`, authenticated) returns a new
      `SseEmitter(emitterTimeout)`, first sends `retry: 5000` and a `ready` event; beyond `maxSubscribers` answers
      `503` `ProblemDetail`. No replay: on reconnect clients refetch through `GET /api/pipeline/runs`.
    - Tests: `RunEventBroadcasterTest` (events reach a subscriber in order, payload shape, failed emitter removed,
      full queue drops without blocking, heartbeat), `RunEventsControllerTest` (MockMvc async: `asyncStarted`,
      `ready` event, a `run` event after `runChanged`, 401 without token, 503 at the cap).

13. **End-to-end and documentation.**
    - `RunsApiIntegrationTest` (Testcontainers, `disabledWithoutDocker`; `RunDispatcher` replaced by the core
      `RecordingDispatcher` through a `@TestConfiguration` `@Primary` bean so no HTTP call is made): `POST` stores a
      `MANUAL` run with `requestedBy` and `force`; second `POST` → `409`; with `conflict-mode=QUEUE` → `202` and a
      `pending_trigger` row, and completing the active run (`runs.update` to `FAILED` through the observer path)
      launches it and deletes the row; `GET` list/detail read it back; `PipelineOrchestratorApplicationTest`
      asserts `/api/pipeline/runs` without a token is `401` and health stays public.
    - `tt-league-pipeline-orchestrator-runtime/README.md`: new "Security" (JWT validation, algorithm by secret
      length, authorities, revocation limitation, CORS), "Runs API" (endpoints, request/response examples, status
      mapping, conflict modes) and "Event stream" (event names, payloads, heartbeat, no replay, use `fetch` with the
      `Authorization` header because native `EventSource` cannot send it) sections; configuration tables gain
      `PIPELINE_CORS_ALLOWED_ORIGINS`, `PIPELINE_TRIGGER_CONFLICT_MODE`, `PIPELINE_EVENTS_*`; update the
      `JWT_SIGNING_SECRET` row (32 UTF-8 bytes, same value as the platform `security.jwt.secret`).
    - `tt-league-pipeline-orchestrator-runtime/AGENTS.md`: replace "Spring Security waits for FEAT-00105" with the
      security, controller and event-stream boundaries (tokens never logged; observers never throw; broadcaster pool
      is not an `Executor` bean).
    - `tt-league-pipeline-orchestrator-core/AGENTS.md` and `README.md`: describe the `trigger` package and the
      `CompositeRunObserver` exception rule; list the new fixtures.
    - `docs/pipeline-datamodel.md` (step 6).
    - Validation: `mvn -pl tt-league-pipeline-orchestrator-core -am test`,
      `mvn -pl tt-league-pipeline-orchestrator-runtime -am test` with Docker running, then the full `mvn test`.

## Acceptance Criteria

- [ ] `POST /api/pipeline/runs` (`source` or `ALL`, required `season`, scope `OPEN_MATCH_DAYS`/`GROUP`/`FULL_SEASON` with filters for `GROUP`, `force`) creates one `MANUAL` run per source through the shared `TriggerRun` path, records the JWT subject as `requestedBy` and passes `force` to the ingest run
- [ ] `OPEN_MATCH_DAYS` is resolved through a core `OpenMatchDayScopeResolver` port; until FEAT-00108 provides a resolver it is answered with 422 `SCOPE_UNAVAILABLE`
- [ ] A trigger for a source with an active run is rejected with 409 and a clear message; with `conflict-mode: QUEUE` it is stored as the source's single persisted pending trigger (202), launched when the active run ends or at startup, and a second pending trigger for the source is rejected with 409
- [ ] `GET /api/pipeline/runs` (filters: source, status, from/to; paged, newest first) and `GET /api/pipeline/runs/{id}` return runs with steps, durations, issues and import report
- [ ] `GET /api/pipeline/events` streams run creation, run and step transitions and pending-trigger changes as Server-Sent Events without blocking run execution
- [ ] Platform JWTs are validated with the shared secret (HS256/HS384/HS512 by secret length); viewing needs authentication and triggering needs `matches:write`
- [ ] Controller, security, persistence and integration tests cover validation, 409, queueing and draining, permissions and the event stream

# Implementation Guidelines

- Manual and scheduled runs share one code path and one queue (proposal "Run manager"): `TriggerRun` is the only
  caller of `RunLauncher` for new runs. FEAT-00106 calls it with trigger `SCHEDULED`, `requestedBy`
  `system:scheduler` and `ConflictMode.REJECT` (skip when active), whatever the manual conflict mode is.
- Core boundaries stay as enforced by `CoreDependencyRulesTest`: the `trigger` package uses JDK types only; Spring
  Security, SSE, JSON and HTTP status mapping live in the runtime.
- Validation is explicit: a missing or malformed season, an unknown source or scope type, or filters that do not fit
  the scope type are `400`. There is no default season, source or scope.
- `force` is passed to ingest only (bypasses its no-change skip, FEAT-00097); `allowPublishedShrink` stays `false`
  for every run created here.
- Observers and pending-trigger listeners never throw into the executor; `CompositeRunObserver` is the only broad
  catch and logs every failure. Sending SSE events never happens on a run thread.
- Never log, echo or store a JWT, `X-API-Key` or the signing secret. Tokens are accepted only in the
  `Authorization` header, never in a query string (they would land in access logs).
- One Flyway migration (`V2`); never edit `V1`. Update `docs/pipeline-datamodel.md` in the same change.
- Out of scope: `RETRY` runs and a retry endpoint, cancelling runs, deleting a pending trigger, event replay
  (`Last-Event-ID`), checking the platform's logout blacklist, the scheduler (FEAT-00106), the real open-match-day
  resolver (FEAT-00107/FEAT-00108) and the frontend (FEAT-00109/FEAT-00110).

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Decision D7 (auth). `ALL` creates one run per source.

2026-10-04 — plan built (status `idea` → `planned`, effort `medium` → `large`). Decisions confirmed by the user:
- **Open match days:** `OPEN_MATCH_DAYS` is in the API contract now and resolved through the core
  `OpenMatchDayScopeResolver` port. The runtime ships an "unavailable" resolver (`422 SCOPE_UNAVAILABLE`,
  `@ConditionalOnMissingBean`) that FEAT-00108 replaces. Acceptance criteria updated accordingly.
- **Queue mode:** persisted `pending_trigger` table, at most one per source; a further trigger while one is pending
  is `409 PENDING_EXISTS`. It is launched when the source's active run ends (observer) or at startup
  (`drainIdle`). Default `conflict-mode` stays `REJECT`. The request (scope type and filters) is stored and resolved
  at launch, so `OPEN_MATCH_DAYS` reflects the state after the active run.
- **Season:** required `season` in the request body (`400` when missing or malformed); no configured fallback.

Other decisions made while planning:
- `ALL` processes sources independently (no all-or-nothing). HTTP status: `201` if any run was created, else `202`
  if any trigger was queued, else `409` if any was rejected, else `422`; the body always lists every source's
  outcome. `ALL` with `GROUP` is `400` because group filters are source-specific.
- The platform signs with jjwt `signWith(key)`, which picks HS256/HS384/HS512 from the secret length, so the
  decoder picks the same algorithm; decision D7's "HS256" holds for 32-47-byte secrets. The secret check moves from
  32 characters to 32 UTF-8 bytes to match the platform.
- Authorities come from the token's `permissions` claim (plus `ROLE_<role>`); `requestedBy` is the JWT `sub`
  (platform username). Revoked (logged-out) tokens stay valid here until they expire: accepted limitation,
  documented in the README.
- Scope type is not persisted on `pipeline_run`; the API shows the filters and `fullSeason`. Revisit if FEAT-00110
  needs to tell `OPEN_MATCH_DAYS` runs apart from `GROUP` runs.

Follow-ups for dependent features:
- FEAT-00106: call `TriggerRun` with `SCHEDULED`/`REJECT`; its `ShedLock` migration becomes `V3`.
- FEAT-00108: provide an `OpenMatchDayScopeResolver` bean (replaces the unavailable one) and use the
  `NO_OPEN_MATCH_DAYS` code for an empty result.
- FEAT-00109: the SSE hook must use `fetch` streaming with the `Authorization` header (native `EventSource` cannot
  send it) and refetch `GET /api/pipeline/runs` after reconnecting (no replay). Set
  `PIPELINE_CORS_ALLOWED_ORIGINS` for the frontend origin unless both apps sit behind one proxy (FEAT-00116).

2026-10-04 — plan approved by the user; status `planned` → `ready`.
