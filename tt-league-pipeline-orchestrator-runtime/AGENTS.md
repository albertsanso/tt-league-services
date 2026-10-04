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
