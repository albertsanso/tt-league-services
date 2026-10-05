# Pipeline orchestrator runtime instructions

These instructions supplement the repository-level `AGENTS.md`.

## Scope

Executable Spring Boot 3.5 (Java 21) orchestrator service. It adapts the ports
of `tt-league-pipeline-orchestrator-core`: HTTP clients, controllers, scheduler
and the Flyway/JPA persistence of the run model (later features add security).

## Boundaries

- Depends only on `tt-league-pipeline-orchestrator-core` among project modules.
  Never depend on `tt-data-league-*` modules.
- The platform (`tt-data-league-api-runtime`) and `tt-league-ingest-rest` are
  reached over HTTP only. Never read or write platform tables.
- Configuration lives in `PipelineOrchestratorProperties`
  (`tt.pipeline.*`, validated). Required values have no defaults in
  `application.yml` (database, platform and ingest URLs and keys, artifact
  directory), so a missing variable fails startup. Do not add defaults. Only
  the tuning values documented in the README (poll intervals, retry, timeouts,
  concurrency, recovery) have defaults.
- HTTP calls to the platform and ingest go only through the gateways in
  `gateway` (`IngestServiceJobRunner`, `HttpImportGateway`); never log or echo
  an `X-API-Key` value or put one in a `RunError` message.
- The run dispatcher (`ExecutorRunDispatcher`) owns a private pool and must
  never be an `Executor`/`TaskExecutor` bean: Boot `applicationTaskExecutor`
  backs off when one exists.
- Artifacts are written only through `FileSystemArtifactStore`; the artifact
  directory must exist and be writable at startup.
- Flyway, JPA and Testcontainers are in place. Security is stateless bearer
  JWT (platform tokens, algorithm by secret length); never log, echo or store
  a token, key or the signing secret, and accept tokens only in the
  `Authorization` header.
- Controllers (`api/`) only translate HTTP to the core `TriggerRun` and
  `RunQueryService`; new runs are created only through `TriggerRun`.
- Scheduled runs come only from `ScheduledRunTrigger` (`schedule/`), which calls
  the core `ScheduledRunTick` under a ShedLock lock taken through
  `LockingTaskExecutor`. Its scheduler is private and never a bean; do not add
  `@EnableScheduling` or `@EnableSchedulerLock` (Boot would register a
  `taskScheduler` `Executor` bean). Schedules are opt-in per source: no
  default cron, season or zone.
- The match-day tracker (`tracker/`) recomputes only through `TrackerRecomputeDispatcher` (one private
  single-thread executor, never an `Executor` bean); the `TrackerRunObserver` and the periodic
  `TrackerRecomputeSchedule` (ShedLock lock `pipeline-tracker-recompute`, private scheduler, not a bean) only
  enqueue. Platform match state is read only through `HttpPlatformMatchGateway`, which needs the service
  credential to hold `matches:read`; the controllers in `api/` only call `MatchDayActions`,
  `MatchDayQueryService`, `MatchDayResultsService` and the core `MatchDayRefresh` (which still creates runs only
  through `TriggerRun`; `POST /api/pipeline/runs` and the refresh share the answer mapping in `TriggerResponses`).
  The completion category lives only in `TrackerRules.completion`; the API and the UI never derive it. Results are
  read through from the platform for `GET /{id}/results` and are never stored, cached or logged. Never add retries,
  partial writes or fallbacks to a failed recompute, and never store match results. The `MatchDayChangeListener`
  (implemented by the `RunEventBroadcaster`) is told after a recompute that changed something and after operator
  actions; its failures are logged and never reach the dispatcher or the request.
- Adaptive polling runs only from `AdaptivePollingTrigger` (`polling/`), which calls the core `AdaptivePollingTick`
  per source under the ShedLock lock `pipeline-polling-<SOURCE>` through `LockingTaskExecutor`; a failing source is
  logged and never stops the others. Its scheduler is private and never a bean. A source has either a cron or adaptive
  polling (startup fails otherwise), and the season and zone are the schedule ones. The `OPEN_MATCH_DAYS` resolver is
  always the tracker-backed `TrackerOpenMatchDayScopeResolver` from `PollingConfiguration`. The ingest status is read
  only through `HttpIngestStatusGateway`; the policy and schedule endpoints are in `PollingController` (policy
  changes `ADMIN`, resume `matches:write`).
- Run observers never throw into the executor (`CompositeRunObserver` isolates
  them). The `RunEventBroadcaster` sends on its own private pool, which is not
  an `Executor` bean, and never on a run thread.
- Schema `pipeline` changes only through new `db/migration/V<n>__*.sql`
  migrations; never edit an applied one. `ddl-auto` stays `validate`. Update
  `docs/pipeline-datamodel.md` with every migration, and never reference
  platform tables.
- Persistence tests use Testcontainers PostgreSQL and are skipped without
  Docker; run them with Docker before reporting persistence work as verified.

## Validation

```text
mvn -pl tt-league-pipeline-orchestrator-runtime -am test
```
