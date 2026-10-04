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
| `PIPELINE_PLATFORM_API_KEY` | `tt.pipeline.platform.api-key` | Raw platform service-credential key (FEAT-00101) with the `imports:write` and `matches:read` scopes, sent as `X-API-Key` (not blank) |
| `PIPELINE_ARTIFACTS_DIR` | `tt.pipeline.artifacts.dir` | Existing, writable directory for the package ZIPs |
| `JWT_SIGNING_SECRET` | `tt.pipeline.security.jwt-secret` | Platform `security.jwt.secret`: same value, at least 32 UTF-8 bytes (HS256/HS384/HS512 by length) |
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
| `PIPELINE_SCHEDULE_LOCK_AT_MOST_FOR` | `PT10M` | Longest a scheduled-tick lock is held if its instance dies |
| `PIPELINE_SCHEDULE_LOCK_AT_LEAST_FOR` | `PT30S` | Shortest a scheduled-tick lock is held (clock skew between instances; at most the above) |
| `PIPELINE_TRACKER_RECOMPUTE_INTERVAL` | `PT1H` | Fixed delay of the periodic match-day recompute |
| `PIPELINE_TRACKER_LOCK_AT_MOST_FOR` | `PT10M` | Longest the periodic recompute lock is held if its instance dies |
| `PIPELINE_TRACKER_LOCK_AT_LEAST_FOR` | `PT30S` | Shortest the periodic recompute lock is held (at most the above) |

Scheduled runs are off unless configured; see [Scheduled runs](#scheduled-runs) for the per-source cron variables.

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

## Security

Every `/api/**` request needs a platform JWT in `Authorization: Bearer ...`. The decoder uses the shared secret and
picks HS256 (32-47 bytes), HS384 (48-63) or HS512 (64+) like the platform. Authorities are the `permissions` claim as
is plus `ROLE_<role>`; `sub` is the user name. `POST /api/pipeline/runs` and every mutation under `/api/pipeline/match-days` (`POST`, `PUT`, `DELETE`) need
`matches:write`; everything else needs any valid token. Health, info, `/v3/api-docs` and `/swagger-ui` are public. Tokens revoked by a platform logout stay
valid here until they expire. `PIPELINE_CORS_ALLOWED_ORIGINS` (comma-separated origins, default none) enables CORS
for those browser origins.

## Runs API

- `POST /api/pipeline/runs` `{source: RFETM|BCNESA|FCTT|ALL, season, scopeType: OPEN_MATCH_DAYS|GROUP|FULL_SEASON,
  filters, force}`: one MANUAL run per source, `requestedBy` = token subject. `201` when any run was created, `202`
  when only queued, `409` when rejected (active run, or a pending trigger already exists), `422` when the scope is
  unavailable (`OPEN_MATCH_DAYS` answers `SCOPE_UNAVAILABLE` until its scope resolver exists). The body lists each
  source's outcome under `results`.
- `GET /api/pipeline/runs` (`source`, `status`, `from`, `to`, `page`, `size` up to 100; newest first) and
  `GET /api/pipeline/runs/{id}` (steps, artifacts, import report, issues).
- `GET /api/pipeline/pending-triggers`.
- Conflict mode `PIPELINE_TRIGGER_CONFLICT_MODE`: `REJECT` (default) or `QUEUE` (one persisted pending trigger per
  source, launched when the active run ends or at startup).

## Scheduled runs

Each source can get full-season runs on a fixed schedule. A source is scheduled only when its cron is set; there is
no default schedule.

| Variable | Property | Description |
| --- | --- | --- |
| `PIPELINE_SCHEDULE_RFETM_CRON` | `tt.pipeline.schedule.sources.RFETM.cron` | Cron for RFETM; empty means never |
| `PIPELINE_SCHEDULE_BCNESA_CRON` | `tt.pipeline.schedule.sources.BCNESA.cron` | Cron for BCNESA; empty means never |
| `PIPELINE_SCHEDULE_FCTT_CRON` | `tt.pipeline.schedule.sources.FCTT.cron` | Cron for FCTT; empty means never |
| `PIPELINE_SCHEDULE_SEASON` | `tt.pipeline.schedule.season` | Season of the scheduled runs, e.g. `2025-2026`; required when any cron is set |
| `PIPELINE_SCHEDULE_ZONE` | `tt.pipeline.schedule.zone` | Time zone of the cron expressions, e.g. `Europe/Madrid`; required when any cron is set |

- Crons use the Spring six-field format (`second minute hour day-of-month month day-of-week`), for example
  `0 0 7,22 * * *` for 07:00 and 22:00 every day. A five-field Unix cron, an invalid expression, or a cron without
  a valid season and zone fails startup with the offending setting named.
- A tick creates a `SCHEDULED` run (`FULL_SEASON`, not forced, `requestedBy` = `system:scheduler`) through the same
  `TriggerRun` path as `POST /api/pipeline/runs`. A source with an active run is skipped (logged), whatever
  `PIPELINE_TRIGGER_CONFLICT_MODE` says; ticks never create pending triggers.
- Ticks missed while the service is down are not caught up. A failed tick is logged and the next one still fires.
- With several orchestrator instances, ShedLock (table `pipeline.shedlock`, database clock) lets only one fire each
  tick: the lock `pipeline-schedule-<SOURCE>` is held at least `PIPELINE_SCHEDULE_LOCK_AT_LEAST_FOR` and at most
  `PIPELINE_SCHEDULE_LOCK_AT_MOST_FOR`. Keep the shortest interval between two ticks of a source above
  `PIPELINE_SCHEDULE_LOCK_AT_LEAST_FOR`. The active-run index still rejects a duplicate run if two ticks race.

## Match-day tracker

The tracker keeps operational state for the platform jornadas that are in play: which match days are open and which of
their matches are still waiting for a result. The platform stays the only source of match states; the tracker never
derives postponed, overdue or awaiting-result from dates, and stores no results.

- **Triggers.** Every run that reaches a final status (`NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED`) requests a
  recompute of its source and season, and a periodic recompute (`PIPELINE_TRACKER_RECOMPUTE_INTERVAL`, under the
  ShedLock lock `pipeline-tracker-recompute`) requests one without a run for every source and season that still has
  an unclosed match day and for each source with a fixed schedule. Recomputes run only through
  `TrackerRecomputeDispatcher`, one at a time on a private thread. A failed recompute (platform unavailable, a
  snapshot where round progress and calendar disagree, a concurrent write) writes nothing, is logged at WARN and is
  repaired by the next trigger. The first run of a source bootstraps its tracking: with nothing tracked and no
  schedule the periodic tick does nothing.
- **Platform reads.** `GET /api/v1/match/round-progress` (without `onlyOpen`) and `GET /api/v1/match/calendar`, once
  per competition with a selected jornada, both with `X-API-Key` = `PIPELINE_PLATFORM_API_KEY`. That service
  credential must list `imports:write,matches:read`; without `matches:read` every recompute fails with a 403 that
  names the API key. Open jornadas without a competition cannot be read through the calendar and are skipped with a
  warning.
- **Rules.** A match day is created for a jornada the platform reports open and is `UPCOMING`, `OPEN` or `CLOSED`.
  Match statuses `SCHEDULED`, `AWAITING_RESULT`, `REPORTED`, `POSTPONED` and `OVERDUE` map from the platform
  `calendarState`; `reported_at` and `reported_run_id` record the first run that saw the result (the recompute time
  and no run for a periodic one). A match day closes when every match is reported, postponed or ignored, reopens by
  itself if a match becomes unresolved again, and a manually closed one only reopens through the reopen action.
  Several match days can be open at once. The full rules and tables are in
  [docs/pipeline-datamodel.md](docs/pipeline-datamodel.md).
- **Endpoints** (`/api/pipeline/match-days`). Reads need any valid token: `GET ?source=&season=&state=&from=&to=&page=&size=`
  (size up to 200; `from`/`to` keep match days whose first-to-last match dates overlap the range) and `GET /{id}`
  (matches and timeline). Actions need `matches:write`, record the token subject and the time in the timeline, and
  answer with the updated detail: `POST /{id}/close`, `POST /{id}/reopen`, `PUT /{id}/matches/{matchId}/ignore`,
  `DELETE /{id}/matches/{matchId}/ignore` (each with an optional `{"note"}` body) and `POST /{id}/notes`
  (`{"text", "matchId"?}`). Errors are problem details with a `code`: `400` invalid input, `404`
  `MATCH_DAY_NOT_FOUND`, `409` `ILLEGAL_TRANSITION` or `STALE_MATCH_DAY`.
- Ignoring a match is a flag next to its status, so the status keeps following the platform and an ignore can be
  undone. Ignoring the last unresolved match closes an open day; reopening a day whose matches are all resolved
  closes it again on the next recompute.

## Event stream

`GET /api/pipeline/events` (Server-Sent Events): `ready`, `run`, `step` and `pending-trigger` events plus `: keep-alive`
comments every `PIPELINE_EVENTS_HEARTBEAT` (default PT15S). There is no replay: after reconnecting, refetch
`GET /api/pipeline/runs`. Native `EventSource` cannot send the `Authorization` header, so use `fetch` streaming.
Limits: `PIPELINE_EVENTS_TIMEOUT` (PT30M), `PIPELINE_EVENTS_MAX_SUBSCRIBERS` (50, then `503`).

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
