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
| `PIPELINE_STATISTICS_ZONE` | `tt.pipeline.statistics.zone` | IANA time zone (for example `Europe/Madrid`) whose local days group every [statistic](#statistics). Required, no default: a wrong default would silently shift the day boundaries |
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
| `PIPELINE_POLLING_TICK_INTERVAL` | `PT5M` | Fixed delay of the adaptive polling tick |
| `PIPELINE_POLLING_LOCK_AT_MOST_FOR` | `PT10M` | Longest a polling-tick lock is held if its instance dies |
| `PIPELINE_POLLING_LOCK_AT_LEAST_FOR` | `PT30S` | Shortest a polling-tick lock is held (at most the above) |
| `PIPELINE_STATISTICS_DAILY_AT` | `00:30` | Time of day, in the statistics zone, of the daily aggregation |
| `PIPELINE_STATISTICS_BACKFILL_DAYS` | `31` | How many past days the first catch-up aggregates (0 to 366) |
| `PIPELINE_MAIL_HOST` | empty | SMTP host; empty switches [notifications](#notifications) off |
| `PIPELINE_MAIL_PORT` | `587` | SMTP port (1 to 65535) |
| `PIPELINE_MAIL_USERNAME` | empty | SMTP user; set together with the password, or both empty |
| `PIPELINE_MAIL_PASSWORD` | empty | SMTP password |
| `PIPELINE_MAIL_STARTTLS` | `true` | Require STARTTLS |
| `PIPELINE_MAIL_FROM` | empty | Sender address (required once the host is set) |
| `PIPELINE_MAIL_TO` | empty | Comma-separated recipient addresses (at least one once the host is set) |
| `PIPELINE_MAIL_SUBJECT_PREFIX` | `[tt-pipeline]` | Prefix of every subject |
| `PIPELINE_ALERTS_EVALUATE_INTERVAL` | `PT15M` | Fixed delay of the periodic alert evaluation |
| `PIPELINE_ALERTS_UNREPORTED_AFTER` | `PT48H` | How long after its date a match may stay unreported |
| `PIPELINE_ALERTS_NO_SUCCESS_WINDOW` | `PT24H` | How long a source may go without a successful run while it has an open match day |
| `PIPELINE_ALERTS_CLOSED_LOOKBACK` | `P1D` | How far back a closed match day still raises an alert |
| `PIPELINE_ALERTS_UNIT_KEYS` | empty (every unit) | Comma-separated unit keys (`tt.pipeline.notifications.unit-failures.unit-keys`, 1 to 64 characters each) that may raise a [unit failure alert](#notifications); empty alerts for every unit |

Scheduled runs are off unless configured; see [Scheduled runs](#scheduled-runs) for the per-source cron variables and
[Adaptive polling](#adaptive-polling) for the alternative that follows the open match days.

HTTP connect timeouts are `PT10S`; read timeouts are `PT1M` (ingest) and `PT5M`
(platform, which validates the upload synchronously). They are set in
`application.yml` (`tt.pipeline.*.connect-timeout`, `read-timeout`).

`/actuator/health` (with the `/actuator/health/liveness` and `/readiness` probes), `/actuator/info` and
`/actuator/prometheus` are exposed; see [Observability](#observability). `PIPELINE_LOG_FORMAT` (default `logstash`) selects the console log format.

## Run execution

`RunLauncher` queues a run and dispatches it to a private pool of
`pipeline-run-N` threads; `RunExecutor` then drives it. A run is split into **units** (FEAT-00117): one per ingest group
of its scope (one category/group/phase/territory/gender identity, with its match days merged) or a single `season` unit for a
full-season run. Units run **one after another** in order, each with its own steps, attempts, deadlines, package ZIP, import
job and import report, so a failing group no longer hides the others. A unit has the statuses `PENDING`, `RUNNING_INGEST`,
`PACKED`, `IMPORTING` and the terminal `NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED`, `SKIPPED`; the run itself is `QUEUED`,
`RUNNING` and then a status derived from its units by `RunOutcomeRules` (all `NO_CHANGES`: `NO_CHANGES`; all succeeded or
unchanged: `SUCCEEDED`; any `PARTIAL`, or failed or skipped units next to successful ones: `PARTIAL`; every unit failed or
skipped: `FAILED`). A unit failing with `INGEST_UNAVAILABLE`, `INGEST_BUSY`, `PLATFORM_UNAVAILABLE`,
`ARTIFACT_STORE_FAILED` or `INTERNAL_ERROR` aborts the run: the units still pending are `SKIPPED` (`UNIT_SKIPPED`). Any other
failure only fails that unit. The unit key (`season`, `legacy` for runs from before the units existed, or the SHA-256 identity of
the group, the same key as an adaptive-polling `scopeKey`) identifies the same group across runs. Each unit follows these
steps:

1. **INGEST.** `POST /api/v1/ingest/runs` with stages `download`, `parse` and
   `package` (mode `snapshot` for a full season, `delta` for a scoped run), then
   poll `GET /api/v1/ingest/runs/{id}`. The ingest `outcome` maps to the run:

   | Ingest outcome | Step | Unit |
   | --- | --- | --- |
   | `NO_CHANGES` | succeeded | `NO_CHANGES` (terminal) |
   | `SUCCEEDED` / `COMPLETED_WITH_ISSUES` with a package | succeeded | `PACKED` |
   | `SUCCEEDED` / `COMPLETED_WITH_ISSUES` without a package | `INGEST_NO_PACKAGE` | `FAILED` |
   | `SOURCE_UNAVAILABLE` | `SOURCE_UNAVAILABLE` (retryable) | retry, then `FAILED` |
   | `FAILED` | `INGEST_FAILED` | `FAILED` |

   While an ingest run is `RUNNING`, the orchestrator reads the optional `progress` object of the ingest run state (stage,
   processed and total items, current item; absent for an ingest service without it) and stores it on the unit when it
   changed: the download and parse stages report real counts, fetch and import only step-level progress. A negative count
   or a total below the processed count is a protocol error.

2. **FETCH_PACKAGE.** Download the ZIP, compare the declared `X-Content-SHA256`
   with the stored SHA-256 and store it as
   `<dir>/<source>/<season>/<runId>/<ordinal>-<first 12 characters of the unit key>/ingest-<ingestRunId>.zip` (one `run_artifact`
   row of the unit). A mismatch deletes the file and fails with `PACKAGE_CHECKSUM_MISMATCH`.
3. **IMPORT.** Upload the ZIP to `POST /api/v1/administration/import/jobs` (the
   orchestrator run id is the job `runId`), poll the job and store the counters
   summed over its seasons in the unit's `import_report`. The unit ends `SUCCEEDED`,
   `PARTIAL` or `FAILED` (`IMPORT_FAILED`).

**Retries.** `UNAVAILABLE` (HTTP 5xx, connection or read failure) from a start
call, the ingest outcome `SOURCE_UNAVAILABLE`, a lost ingest run (404 while
polling, `INGEST_RUN_LOST`) and more than `PIPELINE_MAX_RETRIES` consecutive
failed polls start a new attempt (a new `pipeline_step` row) after an
exponential back-off. A failed poll is first repeated inside the attempt. Every
other failure is final, including `INGEST_BUSY` (409), `IMPORT_SHRINK` (409),
`IMPORT_REJECTED`, `IMPORT_FAILED` and any timeout. Once the unit is `IMPORTING`
the job is only polled, never resubmitted.

**Timeouts.** The deadline of a step kind is its first attempt start plus the
configured timeout, so retries and back-off count toward it. Waits are shortened
to the deadline and a back-off that would cross it is not taken. A step that
passes its deadline fails with `STEP_TIMEOUT` and its unit fails. Ingest has no
cancel API, so a timed-out ingest run keeps running; the next run for that
source can then be answered with 409 (`INGEST_BUSY`).

**Failure codes** (`RunError.code`): `INGEST_UNAVAILABLE`, `INGEST_BUSY`,
`INGEST_REJECTED`, `INGEST_RUN_LOST`, `SOURCE_UNAVAILABLE`, `INGEST_FAILED`,
`INGEST_NO_PACKAGE`, `PACKAGE_UNAVAILABLE`, `PACKAGE_GONE`,
`PACKAGE_CHECKSUM_MISMATCH`, `ARTIFACT_STORE_FAILED`, `PLATFORM_UNAVAILABLE`,
`IMPORT_REJECTED`, `IMPORT_SHRINK`, `IMPORT_JOB_LOST`, `IMPORT_FAILED`,
`STEP_TIMEOUT`, `PROTOCOL_ERROR`, `INTERRUPTED`, `DISPATCH_FAILED`,
`INTERNAL_ERROR` and `UNIT_SKIPPED`.

**Recovery.** At startup (`PIPELINE_RECOVER_ON_STARTUP`) every active run is
dispatched again, oldest first, and continues from its stored unit statuses and step
rows (finished units are not repeated): an `IMPORTING` unit polls its job, a `RUNNING_INGEST` unit keeps polling its
ingest run (a 404 after an ingest restart leads to a new attempt), and an
attempt that was interrupted before its external id was stored is failed as
`INTERRUPTED` (retryable). On shutdown running executions are interrupted and
stay active.

## Security

Every `/api/**` request needs a platform JWT in `Authorization: Bearer ...`. The decoder uses the shared secret and
picks HS256 (32-47 bytes), HS384 (48-63) or HS512 (64+) like the platform. Authorities are the `permissions` claim as
is plus `ROLE_<role>`; `sub` is the user name. `POST /api/pipeline/runs`, `POST /api/pipeline/runs/{id}/replay`, `POST /api/pipeline/runs/{id}/units/{unitId}/retry` and every mutation under `/api/pipeline/match-days` (`POST`, `PUT`, `DELETE`) need
`matches:write`; everything else needs any valid token. Health, info, Prometheus, `/v3/api-docs` and `/swagger-ui` are public. Tokens revoked by a platform logout stay
valid here until they expire. `PIPELINE_CORS_ALLOWED_ORIGINS` (comma-separated origins, default none) enables CORS
for those browser origins.

## Runs API

- `POST /api/pipeline/runs` `{source: RFETM|BCNESA|FCTT|ALL, season, scopeType: OPEN_MATCH_DAYS|GROUP|FULL_SEASON,
  filters, force}`: one MANUAL run per source, `requestedBy` = token subject. `201` when any run was created, `202`
  when only queued, `409` when rejected (active run, or a pending trigger already exists), `422` when the scope is
  unavailable (`OPEN_MATCH_DAYS` answers `NO_OPEN_MATCH_DAYS`, `NO_INGEST_STATUS` or `SCOPE_UNMATCHED`, see
  [Adaptive polling](#adaptive-polling)). The body lists each
  source's outcome under `results`.
- `GET /api/pipeline/runs` (`source`, `status`, `unitKey` (runs that have a unit with that key), `from`, `to`, `page`,
  `size` up to 100; newest first) and `GET /api/pipeline/runs/{id}` (units with their own steps, artifacts, counters, progress,
  storage folder, package link and retry decision; every step and artifact of the run; the import report summed over the
  units; issues). A run summary carries `currentUnitId` and, in lists and events, a `units` summary (without counters);
  `ingestRunId` and `importJobId` of a run are the legacy values, a unit carries its own.
- `POST /api/pipeline/runs/{id}/units/{unitId}/retry` (no body, `matches:write`): re-runs one `FAILED` or `SKIPPED` unit of a
  finished run as a new `UNIT_RETRY` run with that unit's scope (`retryOfRunId`, `retryOfUnitId`); the original run and unit
  stay as they are. `202` with `{runId}` and a `Location`, `404` for an unknown run or unit (or a unit of another run), `409`
  with `code: RUN_ACTIVE` when the run or its source has an active run (a retry is never queued), `422` with `code:
  UNIT_NOT_RETRYABLE` when the unit is not `FAILED` or `SKIPPED`. The decision is `retry: {eligible, reason}` on every unit of
  the run detail (`UnitRetryRules`), plus `retriedBy`, the `UNIT_RETRY` runs created for the unit.
- `GET /api/pipeline/runs/{id}/units/{unitId}/package`: streams the stored ZIP of the unit as `application/zip` through the
  artifact store (`404` for an unknown unit or a unit without a package, `410` with `ARTIFACT_PURGED` when it was purged); the
  unit lists the link as `packageUrl` only while an unpurged ZIP exists.
- `POST /api/pipeline/runs/{id}/replay` (no body): replays the import of a past run, see [Replay](#replay). `201` with the
  new `RETRY` run (`RunSummary`) and its `Location`, `404` for an unknown run, `409` with `code: ACTIVE_RUN` and
  `activeRunId` when the source has an active run (a replay is never queued), `422` with `code` `RUN_ACTIVE`,
  `NO_PACKAGE` or `ARTIFACT_PURGED` when the run cannot be replayed. `GET /api/pipeline/runs/{id}` carries the same
  decision as `replay: {allowed, code}`, plus `purgedAt` on every artifact and `importJobReused` on steps and runs.
- `GET /api/pipeline/pending-triggers`.
- Conflict mode `PIPELINE_TRIGGER_CONFLICT_MODE`: `REJECT` (default) or `QUEUE` (one persisted pending trigger per
  source, launched when the active run ends or at startup).

## Replay

A replay creates a `RETRY` run linked to the original (`retryOfRunId`). It never calls ingest and never downloads from the
federation: it re-hashes the original's stored ZIP against its recorded SHA-256 and submits it to the platform again, then
follows the import like any run. The platform keeps its content deduplication: when it already holds an active,
`SUCCEEDED` or `PARTIAL` job for the same content it returns that job (`200`, `created=false`), the `IMPORT` step records
`importJobReused = true` and nothing is re-imported. A replay therefore re-imports only runs whose import `FAILED`, was
rejected or never ran (for example after a platform fix). A replay of an `IMPORT_SHRINK` failure fails the same way: the
published-acta shrink guard is never bypassed.

A run can be replayed when it is finished and at least one of its units has a ZIP (`NO_CHANGES` runs and runs that failed
before any package was stored have none). The replay plans the same units, each shares the ZIP file of the original's unit
with the same ordinal (a unit without a retained package fails with `ARTIFACT_PURGED` while the others replay) and stays
replayable itself. To re-run only one failed unit, including its ingest, use the unit retry of the [Runs API](#runs-api).

## Artifact retention

Artifacts are kept forever unless `tt.pipeline.retention` is configured. With it, a cleanup job
(`ArtifactCleanupSchedule`: private scheduler, ShedLock lock `pipeline-artifact-cleanup`, no catch-up at startup) deletes
the files of expired artifacts and sets `purged_at` on their rows; run and artifact rows are history and are never deleted.
There is no default cron, zone or rule: with the block, startup fails naming the setting when the cron or zone is missing or
invalid, when an artifact kind has no rule, when a rule has both or neither of `max-age` and `seasons`, or when a value is out
of range.

```yaml
tt:
  pipeline:
    retention:
      cron: "0 30 4 * * *"      # Spring six-field cron
      zone: Europe/Madrid        # IANA zone of the cron
      rules:                     # a rule for every kind: zip, manifest, raw, json
        zip:
          seasons: 2             # keep the newest 2 seasons for which the source has runs (1-10)
        manifest:
          seasons: 2
        raw:
          max-age: P90D          # ISO-8601 duration
        json:
          max-age: P90D
```

- `seasons: N` keeps the artifacts of the newest N seasons for which the source has orchestrator runs. When the first run of
  a new season is created, the previous season's ZIPs expire under `seasons: 1`; use `seasons: 2` to keep them through the
  season change.
- A file referenced by an active run is never purged, and a failed delete is logged and counted without stopping the pass;
  that file stays unpurged and the next tick tries again.
- Only `ZIP` artifacts are written today: the rules of the other kinds apply once something writes them.

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

## Adaptive polling

Instead of a fixed cron, a source can be polled adaptively: while match days are open the orchestrator ingests only
the groups that matter, often on match days and rarely otherwise, and stops by itself when a match day is complete.
It is off unless `PIPELINE_POLLING_SOURCES` lists the sources.

| Variable | Property | Description |
| --- | --- | --- |
| `PIPELINE_POLLING_SOURCES` | `tt.pipeline.polling.sources` | Comma-separated sources polled adaptively (for example `RFETM,FCTT`); empty means off |
| `PIPELINE_POLLING_*` | `tt.pipeline.polling.defaults.*` | Policy defaults, below |
| | `tt.pipeline.polling.bcnesa-competition-names` | Map of an `rtb-*` export folder to the stored competition name; unset keeps the 16 entries of the import (`rtb-segona-a` is `Segona _A_`, ...) |

- **Season and zone** come from `PIPELINE_SCHEDULE_SEASON` and `PIPELINE_SCHEDULE_ZONE`, which are required once any
  source is polled. **A source cannot have both a cron and adaptive polling**: the configuration fails at startup
  with "source X has both a cron and adaptive polling". Sources can mix (one on cron, another adaptive).
- **Poll units.** A unit is one ingest group (category, group, phase, territory, gender, limited to the fields the
  source supports) with its open rounds as `matchDays`; RFETM scopes only support the category, so RFETM units are
  per category. The match-day tracker says which match days are open and which matches are unresolved; the ingest
  `match-days-status` rows turn them into ingest scopes. An open tracker match day with no status row fails the
  scope build with `SCOPE_UNMATCHED` (an alert, no run, never a silent full-season fallback); the weekly full refresh
  rewrites the status file and is the recovery path.
- **Levels** (evaluated in the schedule zone; the most urgent level of a unit's unresolved matches wins). Candidate
  matches are the non-ignored `SCHEDULED`, `AWAITING_RESULT`, `OVERDUE` and `POSTPONED` ones of `OPEN` match days, and
  of days closed as `ALL_RESOLVED` that still hold a postponed match; manually closed, removed and upcoming days are
  never polled.

  | Level | Rule | Default interval |
  | --- | --- | --- |
  | `MATCH_DAY` | a match today and now is at least the first start today plus `match-day-start-offset` | `PT2H` |
  | `DAY_AFTER` | latest unresolved match was yesterday | `PT3H` |
  | `DAYS_2_TO_7` | latest unresolved match 2 to 7 days ago | `PT12H` |
  | `OPEN` | open, no match today (future, undated, or today before the offset; the next run is capped at the first start plus the offset) | `PT24H` |
  | `OVERDUE` | only `OVERDUE` matches left, or matches older than 7 days, youngest within `overdue-stop-after-days` | `PT24H` |
  | `STOPPED` | only `OVERDUE` matches older than `overdue-stop-after-days` (21); raises one alert | none |
  | `FULL_REFRESH` | per-source full-season unit: first tick of a source and season (season start) and weekly | `P7D` |

  Defaults (`tt.pipeline.polling.defaults.*`, each with a `PIPELINE_POLLING_<NAME>` variable): `match-day`
  (`PIPELINE_POLLING_MATCH_DAY`), `match-day-start-offset`, `day-after`, `days-two-to-seven`
  (`PIPELINE_POLLING_DAYS_2_TO_7`), `open`, `overdue`, `overdue-stop-after-days`, `full-refresh`,
  `no-change-threshold`. All durations are positive ISO-8601 and must satisfy `match-day <= day-after <=
  days-two-to-seven <= open <= full-refresh` and `overdue <= full-refresh`; an invalid value fails startup.
  An admin can override every value per source through the API (below).
- **Back-off.** After `no-change-threshold` (3) consecutive `NO_CHANGES` runs the interval doubles, and doubles again
  every further threshold, capped at the interval of the next slower level (`MATCH_DAY` to `DAY_AFTER` to
  `DAYS_2_TO_7` to `OPEN`/`OVERDUE` to `FULL_REFRESH`). A level change resets the counter; `SUCCEEDED` and `PARTIAL`
  reset it; `FAILED` leaves it (the executor retry rule handles failures). A run's outcome counts for every unit it
  covered (the import report is not per group).
- **Tick.** Every `PIPELINE_POLLING_TICK_INTERVAL` and per source, under the ShedLock lock `pipeline-polling-<SOURCE>`,
  at most one run is launched: the `FULL_REFRESH` run when due (it covers every unit), otherwise one `GROUP` run
  whose scope is the union of the due units. Runs are `SCHEDULED`, `ConflictMode.REJECT` (a source with an active run
  is skipped, nothing queued) and `requestedBy` `system:polling` (fixed-cron runs use `system:scheduler`). Units are
  never run in parallel, and the federation delays of the ingest are never shortened. A concurrent change of a
  schedule row abandons the tick (logged); the next tick starts again.
- **Stop and resume.** A stopped unit is skipped and raises one alert (a WARN log until the notification feature).
  `POST /api/pipeline/polling/schedules/{id}/resume` makes it due now and polls it once before it can stop again.
- **Endpoints** (`/api/pipeline/polling`). Reads need any valid token: `GET /policies`, `GET /policies/{source}`
  (effective settings with `overridden`, `version`, `updatedBy`, `updatedAt`; durations are ISO-8601, for example
  `PT168H`) and `GET /schedules?source=&season=` (level, interval, counters, next run, pending run, stop state).
  `PUT /policies/{source}` (full settings plus `version`, 0 when no override exists) and
  `DELETE /policies/{source}` (back to the configured defaults) need the `ADMIN` role; the author is the token
  subject. `POST /schedules/{id}/resume` needs `matches:write`. Errors are problem details with a `code`: `400`
  invalid settings, `404` `POLL_SCHEDULE_NOT_FOUND`, `409` `STALE_POLICY`, `STALE_SCHEDULE` or `NOT_STOPPED`.
- **`OPEN_MATCH_DAYS` triggers** (`POST /api/pipeline/runs`, with or without adaptive polling) resolve through the same
  scope builder and use the filters of every unit. They answer `422` with `NO_OPEN_MATCH_DAYS` (nothing is open),
  `NO_INGEST_STATUS` (the ingest has no status file or season yet; run a full-season ingest first) or
  `SCOPE_UNMATCHED` (an open match day matches no status row).
- The ingest status is read from `GET /api/v1/ingest/sources/{source}/match-days-status?season=` with the ingest
  `X-API-Key`. The import path-to-identity rules are mirrored in the core `SourceVocabulary`; a change to the import
  rules (or to `BcnesaCompetitionNames`) must update the vocabulary and its tests in the same change.

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
- **Endpoints** (`/api/pipeline/match-days`). Reads need any valid token:
  `GET ?source=&season=&state=&competition=&phase=&undated=&from=&to=&page=&size=` (size up to 200; `from`/`to` keep
  match days whose first-to-last match dates overlap the range; `competition` and `phase` are exact matches on the
  stored values, `competition` being the category such as `TERCERA-masculino`; `undated=true` keeps only match days
  without dates and answers `400` with `field: "undated"` when combined with `from` or `to`); `GET /facets?source=&season=`
  (`{seasons, competitions, phases}`, distinct and sorted: seasons honour the source, competitions and phases honour
  source and season); `GET /{id}` (matches, timeline and `runs`); and `GET /{id}/results`. Actions need
  `matches:write`, record the token subject and the time in the timeline, and answer with the updated detail:
  `POST /{id}/close`, `POST /{id}/reopen`, `PUT /{id}/matches/{matchId}/ignore`,
  `DELETE /{id}/matches/{matchId}/ignore` (each with an optional `{"note"}` body) and `POST /{id}/notes`
  (`{"text", "matchId"?}`). Errors are problem details with a `code`: `400` invalid input, `404`
  `MATCH_DAY_NOT_FOUND`, `409` `ILLEGAL_TRANSITION` or `STALE_MATCH_DAY`.
- **Completion and counts.** Every summary carries `completion` (`COMPLETE`, `IN_PROGRESS`, `HAS_OVERDUE`, `FUTURE`),
  `reportedMatches` and `totalMatches`, computed by `TrackerRules.completion` over the *active* (non-ignored)
  matches: `FUTURE` while the day is `UPCOMING`; otherwise `HAS_OVERDUE` when an active match is overdue (also on a
  manually closed day); otherwise `COMPLETE` when every active match is reported (a day whose matches are all ignored,
  or without matches, is complete); otherwise `IN_PROGRESS`. `matchCounts` still counts every match, ignored ones
  included, and `ignoredMatches` is their sum.
- **Detail `runs`.** The runs that touched the match day, newest first and without steps: the runs referenced by its
  events or by a match's `reportedRunId` (runs whose recompute changed it, runs that first reported one of its
  matches, and refreshes launched from it). A run whose recompute changed nothing for the day is not listed.
- **Results.** `GET /{id}/results` reads the platform competition calendar on demand and answers
  `{matchDayId, platformToday, results: [{matchId, platformStatus, homeGamesWon, awayGamesWon, winnerTeamName}]}`
  for the tracked matches of the day. Results are never stored, cached or logged. When the platform cannot be read the
  answer is `502` with `code: "PLATFORM_UNAVAILABLE"` and the gateway message (which names a rejected API key).
- **Refresh.** `POST /{id}/refresh` with an optional `{"force": false}` body (needs `matches:write`) re-ingests the
  round of the match day in its group: `MatchDayRefresh` builds the ingest filters with the same `ScopeBuilder` and
  source vocabulary as the `OPEN_MATCH_DAYS` scope and creates a `GROUP` run through `TriggerRun`, with the configured
  conflict mode. RFETM scopes only carry the category, so an RFETM refresh covers the whole category. The answer is the
  same as `POST /api/pipeline/runs`: `201` with the run `Location`, `202` when queued, `409` when the source has an
  active run, `422` when the scope is unavailable (`NO_INGEST_STATUS`, `SCOPE_UNMATCHED`). A created or queued refresh
  appends a `REFRESH_REQUESTED` event (with the run id when one was created) to the match day; it works for any state.
- Ignoring a match is a flag next to its status, so the status keeps following the platform and an ignore can be
  undone. Ignoring the last unresolved match closes an open day; reopening a day whose matches are all resolved
  closes it again on the next recompute.

## Event stream

`GET /api/pipeline/events` (Server-Sent Events): `ready`, `run`, `unit`, `step`, `pending-trigger` and `match-days` events plus
`: keep-alive` comments every `PIPELINE_EVENTS_HEARTBEAT` (default PT15S). A `match-days` event is
`{source, season, matchDayId, cause}`: `cause` is `RECOMPUTED` (`matchDayId` null) after a tracker recompute that
changed a match day or match, or `ACTION` (with the id) after an operator action or a created or queued refresh.
Recomputes that change nothing, and failed ones, send nothing. A `run` event carries the run with the summary of its
units; a `unit` event is one unit (status, timings, error and `progress`, plus the import `counters` once the unit ended) and is sent on every status change and when the
progress changed, through the same bounded queue and drop rule as the other events, so progress never slows a run. A client
ignores a `unit` event whose `progress.updatedAt` is older than the one it already has for the same status. There is no replay: after reconnecting, refetch
`GET /api/pipeline/runs`. Native `EventSource` cannot send the `Authorization` header, so use `fetch` streaming.
Limits: `PIPELINE_EVENTS_TIMEOUT` (PT30M), `PIPELINE_EVENTS_MAX_SUBSCRIBERS` (50, then `503`).

## Notifications

Operators get an SMTP e-mail when the pipeline needs attention, so nobody has to watch the UI. The channel is plain-text
e-mail; there is no other channel. Notifications are **opt-in**: they are enabled exactly when `PIPELINE_MAIL_HOST` is
not blank, and with a blank host nothing is sent and no other channel is used. With a host set, `PIPELINE_MAIL_FROM`
and at least one valid `PIPELINE_MAIL_TO` address are required, the port must be 1-65535, the username and password
must be set together and every duration must be positive; otherwise startup fails and names the setting. The
variables are in the table above (`tt.pipeline.notifications.*`).

The settings are deliberately `tt.pipeline.*` and not `spring.mail.*`: those would auto-configure a `JavaMailSender`
bean and Boot's mail health indicator, and `/actuator/health` would go DOWN whenever the SMTP server is unreachable.
The adapter keeps its sender private. SMTP timeouts are constants: connect `10 s`, read `30 s`, write `30 s`.

**Alerts.** Each condition has a kind and a stable key. It is *raised* when it holds and has no active alert row, and
*cleared* (silently) when it stops holding.

| Alert | Key | Raised when | Cleared when |
| --- | --- | --- | --- |
| Match day closed | match day id | the day is `CLOSED` with `closedAt` within `PIPELINE_ALERTS_CLOSED_LOOKBACK` (every close reason, named in the e-mail) | the day is reopened or removed; a later close raises again |
| Two failed runs | source | the two newest finished runs of the source are both `FAILED` | the newest finished run is not `FAILED` (`PARTIAL` breaks the streak) |
| Unit failures | source and unit key | the same unit failed (`FAILED`, never `SKIPPED`) in its two newest finished occurrences; limited to `PIPELINE_ALERTS_UNIT_KEYS` when that is set | the newest occurrence of the unit did not fail |
| Match unreported | match id | the match of an open day is not ignored, still `SCHEDULED`, `AWAITING_RESULT` or `OVERDUE`, and its date plus `PIPELINE_ALERTS_UNREPORTED_AFTER` has passed | it is reported, postponed, ignored or removed, or its day is no longer open |
| No recent success | source | the source has an open match day and neither a successful run nor the opening of its earliest open day falls within `PIPELINE_ALERTS_NO_SUCCESS_WINDOW` | a successful run inside the window, or no open day left |

A successful run is `SUCCEEDED` or `NO_CHANGES`. The window of the last alert starts at the opening of the match day, so
a day that has just opened does not alert at once. The e-mails name the source, season, competition, group, phase and
round, the teams and dates (UTC) and, for runs, the run ids and error codes; they never carry a run error message, a
URL, a key or a token.

**Once per condition.** An alert is sent once until it clears; a later recurrence raises a new one and sends again.
The evaluation runs after every run that reaches a final state, after every match-day change (tracker recompute,
operator close or reopen) and every `PIPELINE_ALERTS_EVALUATE_INTERVAL`. One pass sends **one e-mail** with every alert
raised in that pass plus earlier alerts whose send failed. A failed send is logged, the alert keeps `notified_at` null
and the next pass retries; nothing is rethrown into runs, the tracker or requests.

**Polling alerts.** The adaptive-polling alerts (a unit stopped after too many overdue days, open match days that
cannot be scoped) are also sent as their own e-mail, on top of their WARN log lines. They are not stored and not
retried: the polling tick already reports each stop once and each message once per day.

All work runs on one private `pipeline-alerts` thread (`AlertDispatcher`); an unreachable SMTP server never delays a run.
There is no lock between instances: the partial unique index makes raising idempotent, but with two instances a retried
failed send can go out twice. The alerts are stored in the `alert` table (see [docs/pipeline-datamodel.md](docs/pipeline-datamodel.md)).

## Statistics

History and statistics (FEAT-00113). Every figure is computed by `StatisticsRules` in the core; the controller only
validates parameters and calls `StatisticsQueries`, and the dashboard only formats server values. Days are local days in
`PIPELINE_STATISTICS_ZONE` and every response carries the `zone`.

| Figure | Definition |
| --- | --- |
| Arrival | A tracked match with `reportedAt > firstSeenAt`, so its result arrived while it was tracked. Matches first seen already reported are left out of every time-to-report figure and of `matchesReported`. |
| Time to report | `reportedAt - matchDateTime` for arrivals with a match date and `reportedAt >= matchDateTime`. |
| Category | The platform competition name, because the tracker has no separate category. |
| `runs` / `failures` | Terminal runs of the source finished in the day, and those with status `FAILED`. |
| `pendingEndOfDay` | Matches of the source not ignored, whose match day is not `CLOSED`, dated before the end of the day, not reported by then and not `POSTPONED`. Computed from the state at aggregation time, so a day aggregated late is an approximation. |
| Median / p90 | Nearest-rank percentile over the sorted durations (index `ceil(p * n) - 1`). |
| Pending (now) | Not ignored matches of `UPCOMING`/`OPEN` match days with status `SCHEDULED`, `AWAITING_RESULT` or `OVERDUE` and a match date up to now; undated matches are left out. Buckets: under 1 day, 1 to 2, 2 to 7 and over 7 days from the match date (lower bounds inclusive). |
| Corrections | `amendedPlayed` of the import reports, by the day they were received. |
| Source health | HTTP errors, timeouts and parse errors the ingest attempts of the day reported, plus the attempts, the `SOURCE_UNAVAILABLE` ones and those without health data. |

The ingest service counts `http_errors`, `timeouts` and `parse_errors` per stage in its run report; the orchestrator sums
them per ingest attempt (parse errors are `parse_errors + invalid` of the parse and teams stages) and stores them on the
`INGEST` step. An ingest service that does not report them leaves the health unknown (`healthUnknown`); a negative
counter is a protocol error. Corrections come from the platform `amendedPlayed` import counter and **stay at zero
unless the platform runs with `IMPORT_EXECUTION_AMENDED_ACTA_DETECTION=write`**.

Endpoints (`GET`, any authenticated user; `source` is optional and repeatable, `from` / `to` are ISO dates, at most 366
days; a bad or missing parameter is a `400`):

| Path | Parameters | Returns |
| --- | --- | --- |
| `/api/pipeline/statistics/daily` | `from`, `to`, `source` | the stored daily rows (time to report in seconds) and the `zone` |
| `/api/pipeline/statistics/runs` | `from`, `to`, `source`, optional `unitKey` | per day and source: `succeeded`, `noChanges`, `partial`, `failed`; `avgStepSeconds` per source and step kind. With `unitKey`, only the runs that have a finished unit with that key and the steps of that unit |
| `/api/pipeline/statistics/units` | `from`, `to`, `source`, optional `unitKey` | per source and unit key: `label` (of the newest unit), `succeeded`, `noChanges`, `partial`, `failed`, `skipped` and `avgSeconds` (null when no unit of the key ran) |
| `/api/pipeline/statistics/time-to-report` | `season`, `source` | per source and competition, plus a source total (`competition` null): `count`, `medianSeconds`, `p90Seconds` |
| `/api/pipeline/statistics/pending` | optional `season`, `source` | per source: the four age buckets and `overdue`, plus `asOf` |
| `/api/pipeline/statistics/corrections` | `from`, `to`, `source` | per day and source `amendedPlayed`, plus totals |
| `/api/pipeline/statistics/reporting-progress` | exactly one `source`, `season`, optional `competition` | per dated match day: window, counts and one point per date (`reported`, `pending`) |
| `/api/pipeline/statistics/source-health` | `from`, `to`, `source` | per day and source: `httpErrors`, `timeouts`, `parseErrors`, `ingestAttempts`, `sourceUnavailable`, `healthUnknown`; plus totals |

The daily job (`DailyStatsSchedule`) runs at `PIPELINE_STATISTICS_DAILY_AT` in the statistics zone and once one minute
after start, under the ShedLock lock `pipeline-daily-stats` (at most `PT10M`), so only one instance aggregates. A tick
writes one row per source for every complete day from the day after the latest stored one (at most
`PIPELINE_STATISTICS_BACKFILL_DAYS` back) through yesterday, oldest first; a stored day is never recomputed, and today is
never aggregated. A failure is logged as a warning and the next tick catches up again. Statistics never block or fail a
run, a recompute or a request. The run detail also shows the ingest health of each attempt and `amendedPlayed` of the
import report.

## Observability

### Metrics

`GET /actuator/prometheus` serves the Prometheus text format. Every meter carries the tag
`application=tt-league-pipeline-orchestrator-runtime`. Run meters are registered on their first sample, so they appear
after the first run finishes. Counters are per process and reset on restart; there is no persistence of metrics.

| Meter (Prometheus name) | Type | Tags | Meaning |
| --- | --- | --- | --- |
| `pipeline.runs.finished` (`pipeline_runs_finished_total`) | counter | `source`, `trigger`, `outcome`, `error` | One per run that reaches a terminal status (`NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED`); `error` is the failure code or `none` |
| `pipeline.run.duration` (`pipeline_run_duration_seconds_*`) | timer | `source`, `trigger`, `outcome` | `finishedAt - startedAt`; a run that failed at launch has no duration |
| `pipeline.unit.finished` (`pipeline_unit_finished_total`) | counter | `source`, `status` | One per unit that reaches a terminal status (`NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED`, `SKIPPED`); the unit key is never a tag |
| `pipeline.step.duration` (`pipeline_step_duration_seconds_*`) | timer | `source`, `step`, `status` | One sample per finished step attempt (`INGEST`, `FETCH_PACKAGE`, `IMPORT`) |
| `pipeline.matches.pending` (`pipeline_matches_pending`) | gauge | `source`, `age` | Matches still waiting for a result by age bucket (`under_1_day`, `days_1_to_2`, `days_2_to_7`, `over_7_days`) |
| `pipeline.matches.overdue` (`pipeline_matches_overdue`) | gauge | `source` | Pending matches with status OVERDUE |
| `pipeline.match.days.open` (`pipeline_match_days_open`) | gauge | `source` | Open match days |
| `pipeline.metrics.refresh.failures` (`pipeline_metrics_refresh_failures_total`) | counter | none | Failed refreshes of the three gauges above |

Boot's JVM, process and `http_*` meters (including `http_client_requests` for the ingest and platform clients) come
with the registry. The duration timers publish the fixed buckets 10s, 30s, 1m, 5m, 15m, 30m, 1h, 2h, 3h and 6h and no
client-side percentiles. Tags are enum or failure-code names only; a run id, season, scope or message is never a tag.

The three gauges read one cached snapshot built by `OperationalGauges`, so the pending figure is the same one
`GET /api/pipeline/statistics/pending` returns. The snapshot is refreshed by the first scrape that finds it older than
30 seconds; nothing runs while nobody scrapes. When a refresh fails, it is logged as a warning, the failure counter is
incremented and the gauges report `NaN` until a refresh succeeds (a database outage costs one read per 30 seconds).

`/actuator/prometheus` is public, like the health endpoint: it carries aggregate counts and durations only. Do not
route it through a public reverse proxy; scrape it from the internal network. Grafana dashboards, alert rules and the
log stack are deployment concerns and are not committed here.

### Logs

Logs are JSON, one object per line, written to the console. `PIPELINE_LOG_FORMAT` selects the format: `logstash`
(default), `ecs` or `gelf` (Boot's structured formats), or an empty value for the plain-text pattern in local
development. Any other value fails startup. The `logstash` fields are `@timestamp`, `level`, `logger_name`,
`thread_name`, `message` and `stack_trace`, plus the MDC and key-value fields below.

Every line written while a run executes carries `runId` (the orchestrator run id), including the core's own lines and
the gateway lines, and while a unit executes also `unitKey` (a hash, safe in logs; the unit lines add `unit`, its ordinal). The run observer lines add `source`, `status`, `step`, `attempt`, `outcome` and `error`. The
orchestrator sends the same id to `tt-league-ingest` as `correlationId` in the `POST /api/v1/ingest/runs` body, and
ingest writes it as `runId` on every log line of that run (with its own id as `ingestRunId`), so one query,
`runId = <uuid>`, returns the lines of both services. An ingest service from before FEAT-00115 ignores the field.

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

## Container image

`Dockerfile` builds the runtime image from the Boot jar built on the host (the `org.albertsanso` dependencies exist only
in the local Maven repository). The build context is this module directory; `.dockerignore` lets only the jar in.

```text
mvn -pl tt-league-pipeline-orchestrator-runtime -am clean package -DskipTests
docker build -t tt-league/orchestrator-runtime --build-arg GIT_SHA=$(git rev-parse --short HEAD) tt-league-pipeline-orchestrator-runtime
```

The image runs as uid/gid `10001`, exposes `8095`, and its health check calls
`http://localhost:8095/actuator/health/liveness`. The artifact directory `/var/lib/tt-pipeline/artifacts` is a volume
mount point. The image presets `JAVA_TOOL_OPTIONS` (`-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`) and
`PIPELINE_ARTIFACTS_DIR=/var/lib/tt-pipeline/artifacts`; the required database, platform, ingest and `JWT_SIGNING_SECRET`
variables have no default and must be set at run time. Logs stay in the `logstash` JSON format. The Compose project that
wires it to PostgreSQL, the platform, ingest and the proxy is described in [deploy/README.md](../deploy/README.md);
`/actuator/prometheus` must not be routed through a public proxy.
