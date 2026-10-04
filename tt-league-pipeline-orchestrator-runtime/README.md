# tt-league-pipeline-orchestrator-runtime

Spring Boot orchestrator service. Depends on
`tt-league-pipeline-orchestrator-core`; the platform and `tt-league-ingest`
are reached over HTTP only.

## Configuration

All required variables have no default; startup fails when one is missing or
invalid.

| Variable | Property | Description |
| --- | --- | --- |
| `PIPELINE_PLATFORM_URL` | `tt.pipeline.platform.base-url` | Base URL of the platform REST API |
| `PIPELINE_INGEST_URL` | `tt.pipeline.ingest.base-url` | Base URL of `tt-league-ingest-rest` |
| `PIPELINE_INGEST_API_KEY` | `tt.pipeline.ingest.api-key` | `X-API-Key` for the ingest service (not blank) |
| `PIPELINE_PLATFORM_API_KEY` | `tt.pipeline.platform.api-key` | Raw platform service-credential key (FEAT-00101) with the `imports:write` scope, sent as `X-API-Key` (not blank) |
| `PIPELINE_ARTIFACTS_DIR` | `tt.pipeline.artifacts.dir` | Existing, writable directory for the package ZIPs |
| `JWT_SIGNING_SECRET` | `tt.pipeline.security.jwt-secret` | Platform HS256 secret, at least 32 characters |
| `PIPELINE_DB_URL` | `spring.datasource.url` | JDBC URL of the PostgreSQL database holding schema `pipeline` |
| `PIPELINE_DB_USERNAME` | `spring.datasource.username` | Database user (ideally limited to schema `pipeline`) |
| `PIPELINE_DB_PASSWORD` | `spring.datasource.password` | Database password |
| `PIPELINE_SERVER_PORT` | `server.port` | Optional, default `8095` |

Tuning values have defaults and can be overridden; every `Duration` must be
positive (ISO-8601, for example `PT30S`) and invalid values fail startup:

| Variable | Default | Description |
| --- | --- | --- |
| `PIPELINE_INGEST_POLL_INTERVAL` | `PT15S` | Poll interval of an ingest run |
| `PIPELINE_IMPORT_POLL_INTERVAL` | `PT10S` | Poll interval of a platform import job |
| `PIPELINE_MAX_RETRIES` | `3` | Retries per step kind (0 to 10), so at most 4 attempts |
| `PIPELINE_INITIAL_BACKOFF` | `PT30S` | First back-off |
| `PIPELINE_BACKOFF_MULTIPLIER` | `2` | Back-off growth factor (at least 1) |
| `PIPELINE_MAX_BACKOFF` | `PT5M` | Back-off cap (not below the initial back-off) |
| `PIPELINE_INGEST_TIMEOUT` | `PT3H` | INGEST step deadline |
| `PIPELINE_FETCH_PACKAGE_TIMEOUT` | `PT10M` | FETCH_PACKAGE step deadline |
| `PIPELINE_IMPORT_TIMEOUT` | `PT3H` | IMPORT step deadline (above the platform default `PT2H` busy wait) |
| `PIPELINE_MAX_CONCURRENT_RUNS` | `3` | Runs executed at once (1 to 3) |
| `PIPELINE_RECOVER_ON_STARTUP` | `true` | Resume active runs at startup |

HTTP connect timeouts are `PT10S`; read timeouts are `PT1M` (ingest) and `PT5M`
(platform, which validates the upload synchronously). They are set in
`application.yml` (`tt.pipeline.*.connect-timeout`, `read-timeout`).

`/actuator/health` and `/actuator/info` are exposed.

## Run execution

`RunLauncher` queues a run and dispatches it to a private pool of
`pipeline-run-N` threads; `RunExecutor` then drives it:

1. **INGEST.** `POST /api/v1/ingest/runs` with stages `download`, `parse` and
   `package` (mode `snapshot` for a full season, `delta` for a scoped run), then
   poll `GET /api/v1/ingest/runs/{id}`. The ingest `outcome` maps to the run:

   | Ingest outcome | Step | Run |
   | --- | --- | --- |
   | `NO_CHANGES` | succeeded | `NO_CHANGES` (terminal) |
   | `SUCCEEDED` / `COMPLETED_WITH_ISSUES` with a package | succeeded | `PACKED` |
   | `SUCCEEDED` / `COMPLETED_WITH_ISSUES` without a package | `INGEST_NO_PACKAGE` | `FAILED` |
   | `SOURCE_UNAVAILABLE` | `SOURCE_UNAVAILABLE` (retryable) | retry, then `FAILED` |
   | `FAILED` | `INGEST_FAILED` | `FAILED` |

2. **FETCH_PACKAGE.** Download the ZIP, compare the declared `X-Content-SHA256`
   with the stored SHA-256 and store it as
   `<dir>/<source>/<season>/<runId>/ingest-<ingestRunId>.zip` (one `run_artifact`
   row). A mismatch deletes the file and fails with `PACKAGE_CHECKSUM_MISMATCH`.
3. **IMPORT.** Upload the ZIP to `POST /api/v1/administration/import/jobs` (the
   orchestrator run id is the job `runId`), poll the job and store the counters
   summed over its seasons in `import_report`. The run ends `SUCCEEDED`,
   `PARTIAL` or `FAILED` (`IMPORT_FAILED`).

**Retries.** `UNAVAILABLE` (HTTP 5xx, connection or read failure) from a start
call, the ingest outcome `SOURCE_UNAVAILABLE`, a lost ingest run (404 while
polling, `INGEST_RUN_LOST`) and more than `PIPELINE_MAX_RETRIES` consecutive
failed polls start a new attempt (a new `pipeline_step` row) after an
exponential back-off. A failed poll is first repeated inside the attempt. Every
other failure is final, including `INGEST_BUSY` (409), `IMPORT_SHRINK` (409),
`IMPORT_REJECTED`, `IMPORT_FAILED` and any timeout. Once the run is `IMPORTING`
the job is only polled, never resubmitted.

**Timeouts.** The deadline of a step kind is its first attempt start plus the
configured timeout, so retries and back-off count toward it. Waits are shortened
to the deadline and a back-off that would cross it is not taken. A step that
passes its deadline fails with `STEP_TIMEOUT` and the run fails. Ingest has no
cancel API, so a timed-out ingest run keeps running; the next run for that
source can then be answered with 409 (`INGEST_BUSY`).

**Failure codes** (`RunError.code`): `INGEST_UNAVAILABLE`, `INGEST_BUSY`,
`INGEST_REJECTED`, `INGEST_RUN_LOST`, `SOURCE_UNAVAILABLE`, `INGEST_FAILED`,
`INGEST_NO_PACKAGE`, `PACKAGE_UNAVAILABLE`, `PACKAGE_GONE`,
`PACKAGE_CHECKSUM_MISMATCH`, `ARTIFACT_STORE_FAILED`, `PLATFORM_UNAVAILABLE`,
`IMPORT_REJECTED`, `IMPORT_SHRINK`, `IMPORT_JOB_LOST`, `IMPORT_FAILED`,
`STEP_TIMEOUT`, `PROTOCOL_ERROR`, `INTERRUPTED`, `DISPATCH_FAILED` and
`INTERNAL_ERROR`.

**Recovery.** At startup (`PIPELINE_RECOVER_ON_STARTUP`) every active run is
dispatched again, oldest first, and continues from its stored status and step
rows: an `IMPORTING` run polls its job, a `RUNNING_INGEST` run keeps polling its
ingest run (a 404 after an ingest restart leads to a new attempt), and an
attempt that was interrupted before its external id was stored is failed as
`INTERRUPTED` (retryable). On shutdown running executions are interrupted and
stay active.

## Persistence

Flyway owns schema `pipeline` (it creates it and keeps its history table
there); Hibernate only validates (`ddl-auto: validate`). The tables are
documented in [docs/pipeline-datamodel.md](docs/pipeline-datamodel.md). The
module may share the platform database or use its own, and never touches
platform tables.

The persistence tests run against PostgreSQL through Testcontainers and need
Docker; without Docker they are reported as skipped, not passed.

## Build and run

```text
mvn -pl tt-league-pipeline-orchestrator-runtime -am test
java -jar tt-league-pipeline-orchestrator-runtime/target/tt-league-pipeline-orchestrator-runtime-0.0.1-SNAPSHOT.jar
```
