# Pipeline orchestrator data model

Schema contract for the orchestrator's own PostgreSQL schema `pipeline`. Flyway
(`src/main/resources/db/migration`) is the only thing that creates or changes
it; Hibernate runs with `ddl-auto: validate`. Update this document with every
migration.

**No foreign keys into platform tables.** The orchestrator reaches the platform
only over REST. Platform ids (import job id, match ids) are stored as plain
columns. Foreign keys exist only between tables of this schema and use the
default `NO ACTION`: runs are history and are not deleted here.

Enumerated columns are `varchar` with a `CHECK (... IN (...))` listing the
values of the matching enum in `tt-league-pipeline-orchestrator-core`.

## `pipeline_run`

One execution of the pipeline for a source and season.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `uuid` PK | |
| `source` | `varchar(16)` NOT NULL | `RFETM`, `BCNESA`, `FCTT` |
| `season` | `varchar(9)` NOT NULL | `^[0-9]{4}-[0-9]{4}$` |
| `scope` | `jsonb` NOT NULL | `{"scopes":[{category, group, phase, territory, gender, matchDays}]}`, nulls omitted; `{"scopes":[]}` is the whole season |
| `force` | `boolean` NOT NULL DEFAULT false | passed to the ingest run (bypasses its no-change skip); added by `V2` |
| `trigger` | `varchar(16)` NOT NULL | `SCHEDULED`, `MANUAL`, `RETRY` |
| `requested_by` | `varchar(128)` NOT NULL | user id, or `system:scheduler` |
| `retry_of_run_id` | `uuid` NULL | FK `pipeline_run(id)`; set exactly when `trigger = 'RETRY'` |
| `status` | `varchar(16)` NOT NULL | see transition table |
| `created_at`, `started_at`, `finished_at` | `timestamptz` | `started_at` / `finished_at` nullable |
| `ingest_run_id` | `varchar(64)` NULL | ingest service run id |
| `import_job_id` | `uuid` NULL | platform import job (plain column) |
| `error_code` | `varchar(64)` NULL | |
| `error_message` | `text` NULL | |
| `version` | `bigint` NOT NULL DEFAULT 0 | optimistic lock, incremented by the JPA adapter |

Constraint: `CHECK ((trigger = 'RETRY') = (retry_of_run_id IS NOT NULL))`.

Indexes:

- `ux_pipeline_run_active_source` — **unique partial index** on `(source)
  WHERE status IN ('QUEUED', 'RUNNING_INGEST', 'PACKED', 'IMPORTING')`. This is
  what guarantees at most one active run per source; the adapter only turns the
  violation into `ActiveRunConflictException`. `NO_CHANGES`, `SUCCEEDED`,
  `PARTIAL` and `FAILED` are terminal and free the source.
- `ix_pipeline_run_source_created` on `(source, created_at DESC)`.
- `ix_pipeline_run_status` on `(status)`.

### Run status transitions

| From | Allowed next |
| --- | --- |
| `QUEUED` | `RUNNING_INGEST`, `PACKED` (`RETRY` runs only, see [Replay](#replay-and-retention)), `FAILED` |
| `RUNNING_INGEST` | `NO_CHANGES`, `PACKED`, `FAILED` |
| `PACKED` | `IMPORTING`, `FAILED` |
| `IMPORTING` | `SUCCEEDED`, `PARTIAL`, `FAILED` |
| `NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED` | none (terminal) |

Enforced by `RunStatus` and `PipelineRun` in the core, not by the database: `QUEUED -> PACKED` is reachable only through
`PipelineRun.startReplay`, which requires `trigger = RETRY`; `PipelineRun.packed` still requires `RUNNING_INGEST`.

## `pipeline_step`

One attempt of a step of a run. Step retries add rows; they do not change the
run status.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `uuid` PK | |
| `run_id` | `uuid` NOT NULL | FK `pipeline_run(id)` |
| `kind` | `varchar(16)` NOT NULL | `INGEST`, `FETCH_PACKAGE`, `IMPORT` |
| `attempt` | `int` NOT NULL | `>= 1` |
| `status` | `varchar(16)` NOT NULL | `RUNNING`, `SUCCEEDED`, `FAILED` |
| `started_at` | `timestamptz` NOT NULL | |
| `finished_at` | `timestamptz` NULL | |
| `external_ref` | `varchar(64)` NULL | ingest run id or import job id |
| `outcome` | `varchar(32)` NULL | external outcome as received |
| `retryable` | `boolean` NULL | |
| `error_code` / `error_message` | `varchar(64)` / `text` NULL | |
| `log_ref` | `varchar(512)` NULL | filled by a later feature |
| `http_errors`, `timeouts`, `parse_errors` | `bigint` NULL | source health an `INGEST` attempt reported (FEAT-00113, `V8`): the `http_errors` and `timeouts` of every ingest stage, and the `parse_errors + invalid` of the parse and teams stages; each `>= 0`. All three are NULL (unknown: an older ingest service, or an attempt that never finished the ingest run) or all set (`ck_pipeline_step_health_all_or_none`), and only an `INGEST` step may carry them (`ck_pipeline_step_health_ingest_only`) |
| `import_job_reused` | `boolean` NULL | whether the platform answered the import submit with an existing job for the same content (`200`, `created=false`) instead of a new one (`202`); FEAT-00114, `V9`. Only an `IMPORT` step may carry it (`ck_pipeline_step_reused_import_only`). NULL means "not recorded" (rows written before `V9`) |

`UNIQUE (run_id, kind, attempt)`.

## `run_artifact`

Files produced or fetched by a run.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `uuid` PK | |
| `run_id` | `uuid` NOT NULL | FK `pipeline_run(id)` |
| `kind` | `varchar(16)` NOT NULL | `ZIP`, `MANIFEST`, `RAW`, `JSON` |
| `storage_key` | `varchar(512)` NOT NULL | relative path; the core rejects absolute paths and `..` |
| `sha256` | `char(64)` NOT NULL | `^[0-9a-f]{64}$` |
| `size_bytes` | `bigint` NOT NULL | `>= 0` |
| `created_at` | `timestamptz` NOT NULL | |
| `purged_at` | `timestamptz` NULL | set when the retention cleanup deleted the file (FEAT-00114, `V9`); `purged_at >= created_at` (`ck_run_artifact_purged_after_created`). The row is kept as history. NULL for every row written before `V9` |

`UNIQUE (run_id, kind, storage_key)`. Indexes added by `V9`: `ix_run_artifact_storage_key (storage_key)` and the partial
`ix_run_artifact_unpurged (created_at) WHERE purged_at IS NULL`.

Several rows may share one `storage_key`: a replay run carries its own `ZIP` row that points at the file of the original run
(see [Replay and retention](#replay-and-retention)).

## `import_report`

Result of the platform import job of a run; at most one per run.

| Column | Type | Notes |
| --- | --- | --- |
| `run_id` | `uuid` PK | FK `pipeline_run(id)` |
| `import_job_id` | `uuid` NOT NULL | platform job (plain column) |
| `import_status` | `varchar(16)` NOT NULL | `SUCCEEDED`, `PARTIAL`, `FAILED` |
| `files_seen`, `items_persisted`, `skipped`, `processor_failures`, `scheduled_created`, `upgraded_to_played`, `rescheduled`, `partial_actas`, `invalid_actas`, `unresolved_pending_fixtures` | `bigint` NOT NULL | counters summed over the job's seasons, each `>= 0` |
| `amended_played` | `bigint` NOT NULL DEFAULT 0 | stored `PLAYED` matches the platform re-applied from an amended acta (FEAT-00089), summed over the seasons; `>= 0`. Added by `V8`: reports written before it read `0`, which means "not recorded before V8". It stays `0` unless the platform runs with `IMPORT_EXECUTION_AMENDED_ACTA_DETECTION=write` |
| `issues` | `jsonb` NOT NULL | array of strings |
| `raw_report` | `jsonb` NOT NULL | platform job JSON as received |
| `received_at` | `timestamptz` NOT NULL | |

## Written by the run executor

No migration: the columns above already fit every value written by FEAT-00104.

- **Steps.** One row per attempt; the attempt number is the highest stored
  attempt of that kind plus 1. Kinds: `INGEST`, `FETCH_PACKAGE`, `IMPORT`.
- **`pipeline_step.external_ref`.** The ingest run id for `INGEST` (set once the
  ingest `POST` answered, so it is null while the call is in flight) and
  `FETCH_PACKAGE`; the platform import job UUID for `IMPORT`.
- **`pipeline_step.outcome`.** `INGEST`: the ingest outcome (`NO_CHANGES`,
  `SUCCEEDED`, `COMPLETED_WITH_ISSUES`, `SOURCE_UNAVAILABLE`, `FAILED`);
  `FETCH_PACKAGE`: `STORED`; `IMPORT`: `SUCCEEDED`, `PARTIAL` or `FAILED`. Null
  when the attempt failed before an outcome existed.
- **`error_code`** (steps and runs): the `FailureCode` names `INGEST_UNAVAILABLE`,
  `INGEST_BUSY`, `INGEST_REJECTED`, `INGEST_RUN_LOST`, `SOURCE_UNAVAILABLE`,
  `INGEST_FAILED`, `INGEST_NO_PACKAGE`, `PACKAGE_UNAVAILABLE`, `PACKAGE_GONE`,
  `PACKAGE_CHECKSUM_MISMATCH`, `ARTIFACT_STORE_FAILED`, `ARTIFACT_PURGED`, `PLATFORM_UNAVAILABLE`,
  `IMPORT_REJECTED`, `IMPORT_SHRINK`, `IMPORT_JOB_LOST`, `IMPORT_FAILED`,
  `STEP_TIMEOUT`, `PROTOCOL_ERROR`, `INTERRUPTED`, `DISPATCH_FAILED`,
  `INTERNAL_ERROR`. Messages never contain keys.
- **`pipeline_step.import_job_reused`** (`V9`). Written together with the import job id (`external_ref`) when the
  `IMPORT` step receives the platform's answer: `true` for an existing job (`created=false`), `false` for a new one. Every
  run records it, not only replays.
- **`pipeline_run.ingest_run_id`.** The ingest run currently followed: a retried
  `INGEST` step replaces it without a status change.
- **`run_artifact.storage_key`.** `<source lower-case>/<season>/<runId>/ingest-<ingestRunId>.zip`
  for the package ZIP, relative to the configured artifact directory.
- **`import_report`.** Derived from the finished platform job: counters summed
  over the seasons that have a result; `issues` are the job `errorDetail`, then
  each season `errorDetail` as `<season>: <detail>`, then each season
  `executionIssues` as `<season>: <issue>`; `import_status` is the job status
  and `raw_report` the job JSON as received.

## `pending_trigger`

At most one waiting trigger per source (primary key `source`), written by `TriggerRun` in queue conflict mode and
deleted when it is launched or dropped. It stores the request, not the resolved scope.

| Column | Type | Notes |
| --- | --- | --- |
| `source` | `varchar(16)` PK | `RFETM`, `BCNESA`, `FCTT` |
| `season` | `varchar(9)` NOT NULL | `^[0-9]{4}-[0-9]{4}$` |
| `scope_type` | `varchar(16)` NOT NULL | `OPEN_MATCH_DAYS`, `GROUP`, `FULL_SEASON` |
| `filters` | `jsonb` NOT NULL | same `{"scopes":[...]}` layout as `pipeline_run.scope` |
| `force` | `boolean` NOT NULL | |
| `requested_by` | `varchar(128)` NOT NULL | JWT subject |
| `requested_at` | `timestamptz` NOT NULL | |

Extra index on `pipeline_run`: `ix_pipeline_run_created (created_at DESC, id)` for the unfiltered run list.

## `shedlock`

ShedLock JDBC lock table for the fixed-schedule trigger, the periodic tracker recompute and the daily statistics job: one
row per lock name, written only by ShedLock (`JdbcTemplateLockProvider` with the database clock). Lock names are
`pipeline-schedule-<SOURCE>`, `pipeline-tracker-recompute` and `pipeline-daily-stats`; rows are reused, not deleted,
when a lock is released.

| Column | Type | Notes |
| --- | --- | --- |
| `name` | `varchar(64)` PK | lock name |
| `lock_until` | `timestamp(3)` NOT NULL | UTC database time until which the lock is held |
| `locked_at` | `timestamp(3)` NOT NULL | UTC database time the lock was taken |
| `locked_by` | `varchar(255)` NOT NULL | host name of the instance that took it |

## `match_day`

One tracked platform jornada (a round of one group of one competition), written by the match-day tracker
(`MatchDayTracker` through `TrackerRecomputeDispatcher`) and by the operator actions (`MatchDayActions`). It holds
operational state only; results stay in the platform.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `uuid` PK | |
| `source` | `varchar(16)` NOT NULL | `RFETM`, `BCNESA`, `FCTT` |
| `season` | `varchar(9)` NOT NULL | `^[0-9]{4}-[0-9]{4}$` |
| `competition` | `varchar(255)` NOT NULL | platform competition name |
| `group_number` | `integer` NULL | `>= 1` when set |
| `phase` | `varchar(255)` NULL | |
| `round` | `integer` NOT NULL | `>= 1` |
| `first_date`, `last_date` | `date` NULL | first and last match date; both null (undated) or both set with `first_date <= last_date` |
| `grace_days` | `integer` NOT NULL | `>= 0`; the platform overdue grace period at the last recompute |
| `state` | `varchar(16)` NOT NULL | `UPCOMING`, `OPEN`, `CLOSED` |
| `close_reason` | `varchar(16)` NULL | `ALL_RESOLVED`, `MANUAL`, `REMOVED` |
| `closed_at` | `timestamptz` NULL | |
| `closed_by` | `varchar(128)` NULL | user id, or `system:tracker` |
| `opened_at` | `timestamptz` NULL | required when `state = 'OPEN'` |
| `created_at`, `last_recomputed_at` | `timestamptz` NOT NULL | |
| `version` | `bigint` NOT NULL DEFAULT 0 | optimistic lock, incremented by the JPA adapter |

Constraints: `state = 'CLOSED'` exactly when `close_reason`, `closed_at` and `closed_by` are all set (and all null
otherwise); the date pair rules above.

Indexes: `ux_match_day_key` - **unique** on `(source, season, competition, COALESCE(group_number, 0),
COALESCE(phase, ''), round)`, one match day per jornada even when group or phase is absent (an expression index, so
it also works on servers without `NULLS NOT DISTINCT`); `ix_match_day_source_season_state (source, season, state)`;
`ix_match_day_first_date (first_date)`.

State rules (all in the core `TrackerRules`; the database only enforces the invariants above):

- A match day is created only for a jornada the platform reports `open` (or one already tracked). Its window is
  `first_date` to `last_date + grace_days`; an undated jornada has no window.
- `UPCOMING` becomes `OPEN` when the window started, or earlier once any match is `AWAITING_RESULT`, `OVERDUE` or
  `REPORTED`.
- An `OPEN` match day with at least one match closes as `ALL_RESOLVED` when every match is `REPORTED`, `POSTPONED` or
  ignored. A day closed as `ALL_RESOLVED` reopens when a recompute (or an unignore) finds an unresolved match. A
  `MANUAL` close is only undone by the reopen action. A tracked jornada that the platform no longer reports is closed
  as `REMOVED` and its matches are deleted.
- Several match days of a source can be `OPEN` at once.

## `match_tracking`

One platform match inside a match day. Its status is mapped from the platform calendar state, never derived here.

| Column | Type | Notes |
| --- | --- | --- |
| `match_id` | `uuid` PK | platform match id (plain column, no foreign key) |
| `match_day_id` | `uuid` NOT NULL | FK `match_day(id)`; changes when the platform moves the match to another round |
| `status` | `varchar(16)` NOT NULL | `SCHEDULED`, `AWAITING_RESULT`, `REPORTED`, `POSTPONED`, `OVERDUE` |
| `match_date_time` | `timestamptz` NULL | null for an undated match |
| `home_team_name`, `away_team_name` | `varchar(255)` NULL | |
| `first_seen_at`, `status_changed_at`, `last_seen_at` | `timestamptz` NOT NULL | |
| `reported_at` | `timestamptz` NULL | set when the match first becomes `REPORTED`; cleared when it leaves `REPORTED` |
| `reported_run_id` | `uuid` NULL | FK `pipeline_run(id)`; the run that saw the result, null for a periodic recompute |
| `ignored_at`, `ignored_by` | `timestamptz` / `varchar(128)` NULL | both set or both null; an ignored match counts as resolved while its status keeps following the platform |
| `version` | `bigint` NOT NULL DEFAULT 0 | optimistic lock |

Constraints: `status = 'REPORTED'` exactly when `reported_at` is set; `reported_run_id` needs `reported_at`; the
ignored pair rule. Index `ix_match_tracking_day (match_day_id)`.

Status mapping from the platform `calendarState`: `PLAYED` -> `REPORTED`, `AWAITING_RESULT` -> `AWAITING_RESULT`,
`OVERDUE` -> `OVERDUE`, `POSTPONED` -> `POSTPONED`, `UPCOMING` and `UNDATED` -> `SCHEDULED`; any other value is a
protocol error and the recompute writes nothing. `POSTPONED` matches stay under their original match day after it
closes and keep being refreshed while the platform reports the jornada open.

## `match_day_event`

Append-only timeline of a match day: every lifecycle change and operator action.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `uuid` PK | |
| `match_day_id` | `uuid` NOT NULL | FK `match_day(id)` |
| `match_id` | `uuid` NULL | platform match id, **no foreign key** so it survives `MATCH_REMOVED` |
| `kind` | `varchar(32)` NOT NULL | `OPENED`, `CLOSED`, `REOPENED`, `MATCH_REPORTED`, `MATCH_IGNORED`, `MATCH_UNIGNORED`, `MATCH_REMOVED`, `NOTE`, `REFRESH_REQUESTED` |
| `actor` | `varchar(128)` NOT NULL | JWT subject, or `system:tracker` for lifecycle changes |
| `occurred_at` | `timestamptz` NOT NULL | |
| `run_id` | `uuid` NULL | FK `pipeline_run(id)`; the run whose recompute made the change, or the run an operator launched with `REFRESH_REQUESTED` |
| `note` | `varchar(2000)` NULL | required (`CHECK`) when `kind = 'NOTE'` |

Index `ix_match_day_event_day (match_day_id, occurred_at)`.

Writers: the tracker recompute writes `match_day`, `match_tracking` and system events in one transaction per
recompute (`MatchDayRepository.apply`); a version conflict or a concurrently created `ux_match_day_key` row aborts it
with `StaleMatchDayException` and nothing is written. The operator actions write the changed rows and their event in
one transaction the same way.

## `poll_schedule`

Adaptive polling state (FEAT-00108): one row per poll unit, that is per `(source, season, scope_key)`. A unit is an
ingest group (category, group, phase, territory, gender, limited to the fields the source supports) with the match
days still open, or the per-source `FULL_REFRESH` unit that runs the whole season weekly and at season start.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `uuid` PK | |
| `source` | `varchar(16)` NOT NULL | `RFETM`, `BCNESA`, `FCTT` |
| `season` | `varchar(9)` NOT NULL | `^[0-9]{4}-[0-9]{4}$` |
| `scope_key` | `varchar(64)` NOT NULL | SHA-256 hex of the unit identity **without its match days** (so back-off state survives a new round), or the literal `FULL_REFRESH` |
| `kind` | `varchar(16)` NOT NULL | `FULL_REFRESH`, `GROUP` |
| `filter` | `jsonb` NULL | `{"scopes":[{...}]}` with exactly one filter (the unit and its open rounds); NULL exactly for `FULL_REFRESH` |
| `policy_level` | `varchar(16)` NOT NULL | `MATCH_DAY`, `DAY_AFTER`, `DAYS_2_TO_7`, `OPEN`, `OVERDUE`, `STOPPED`, `FULL_REFRESH` (`FULL_REFRESH` exactly for the `FULL_REFRESH` unit) |
| `interval_seconds` | `bigint` NULL | effective interval, back-off included; NULL exactly while `STOPPED`; `> 0` |
| `consecutive_no_change` | `integer` NOT NULL DEFAULT 0 | consecutive `NO_CHANGES` outcomes at the current level; reset by a level change, `SUCCEEDED` or `PARTIAL`, kept by `FAILED` |
| `next_run_at` | `timestamptz` NULL | NULL while `STOPPED` |
| `last_run_at` | `timestamptz` NULL | end of the last run that covered the unit; NULL for a never polled (or resumed) unit |
| `pending_run_id` | `uuid` NULL | FK `pipeline_run(id)` `ON DELETE SET NULL`; the run launched for the unit, until its outcome is applied |
| `stopped_at` | `timestamptz` NULL | set exactly while `policy_level = 'STOPPED'` |
| `stop_reason` | `varchar(32)` NULL | `OVERDUE_LIMIT`; set exactly when `stopped_at` is |
| `alerted_at` | `timestamptz` NULL | `GROUP`: when the stop alert was raised (once per stop); `FULL_REFRESH`: when the last unmatched-scope alert was raised |
| `version` | `bigint` NOT NULL | optimistic version (`@Version`), starts at 0 |
| `created_at`, `updated_at` | `timestamptz` NOT NULL | |

Unique index `ux_poll_schedule_unit (source, season, scope_key)`; index `ix_poll_schedule_next_run (source, season,
next_run_at)`. Checks tie `kind`, `filter`, `scope_key` and `policy_level` together for the `FULL_REFRESH` unit, and
`policy_level = 'STOPPED'` to `stopped_at`/`stop_reason` and to a NULL interval.

Writers: only the adaptive tick (`AdaptivePollingTick`, under the lock `pipeline-polling-<SOURCE>`) and the resume
endpoint, always through `PollScheduleRepository.save`. A version conflict, a removed row or a concurrently created
unit raises `StalePollScheduleException`; the tick then stops for that source and starts again at the next interval.
Group rows are deleted when their match days are no longer open; the `FULL_REFRESH` row is kept.

## `poll_policy`

Per-source override of the polling settings; a source without a row uses the configured defaults
(`tt.pipeline.polling.defaults.*`).

| Column | Type | Notes |
| --- | --- | --- |
| `source` | `varchar(16)` PK | `RFETM`, `BCNESA`, `FCTT` |
| `settings` | `jsonb` NOT NULL | `matchDay`, `matchDayStartOffset`, `dayAfter`, `daysTwoToSeven`, `open`, `overdue`, `fullRefresh` (ISO-8601 durations), `overdueStopAfterDays`, `noChangeThreshold` (integers); read strictly, so unknown or missing keys fail |
| `version` | `bigint` NOT NULL | optimistic version, starts at 1 for the first save |
| `updated_by` | `varchar(128)` NOT NULL | JWT subject of the admin |
| `updated_at` | `timestamptz` NOT NULL | |

Written only by `PUT`/`DELETE /api/pipeline/polling/policies/{source}`; a version conflict raises
`StalePollPolicyException`.

## `alert`

One row per raised alert condition (FEAT-00112). `condition_key` is a snapshot: the id of the match day or match, or the
source name, so there are deliberately **no foreign keys** and an alert outlives the rows it describes.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `uuid` PK | generated by the evaluator |
| `kind` | `varchar(32)` NOT NULL | CHECK: `MATCH_DAY_CLOSED`, `RUN_FAILURES`, `MATCH_UNREPORTED`, `NO_RECENT_SUCCESS` |
| `condition_key` | `varchar(255)` NOT NULL | match day id (`MATCH_DAY_CLOSED`), match id (`MATCH_UNREPORTED`) or source name (`RUN_FAILURES`, `NO_RECENT_SUCCESS`) |
| `source` | `varchar(16)` | nullable; CHECK: `RFETM`, `BCNESA`, `FCTT` |
| `season` | `varchar(16)` | nullable |
| `title` | `varchar(255)` NOT NULL | plain text, also the e-mail subject of a single alert |
| `detail` | `text` NOT NULL | plain text; run failures carry `RunError.code`, never `RunError.message` |
| `raised_at` | `timestamptz` NOT NULL | |
| `notified_at` | `timestamptz` | null until a send succeeded |
| `notify_attempts` | `integer` NOT NULL DEFAULT 0 | CHECK `>= 0`; counts failed and successful sends |
| `last_failure` | `varchar(128)` | exception simple name of the last failed send, never a message |
| `cleared_at` | `timestamptz` | set when the condition stopped holding; a cleared alert never changes again |
| `version` | `bigint` NOT NULL | optimistic version, starts at 0 |

Indexes: the partial unique index `ux_alert_active (kind, condition_key) WHERE cleared_at IS NULL` allows one uncleared
alert per condition, so raising is idempotent across instances (`JpaAlertRepository` turns the violation into
`ActiveAlertExistsException`); `ix_alert_raised (raised_at)`.

Written only by `AlertEvaluator` through `JpaAlertRepository`: it raises a row when a condition starts holding, marks it
notified (or failed, for a retry on the next pass) and clears it when the condition stops holding. A version conflict
raises `StaleAlertException` and the next pass repairs the row.

## `daily_stats`

One snapshot row per source and local day (FEAT-00113, `V8`). It is a snapshot, so there are deliberately **no foreign
keys**: it outlives the rows it was computed from.

| Column | Type | Notes |
| --- | --- | --- |
| `stat_date` | `date` | local day in `zone`; part of the primary key |
| `source` | `varchar(16)` | CHECK: `RFETM`, `BCNESA`, `FCTT`; part of the primary key |
| `runs` | `integer` NOT NULL | terminal runs of the source with `finished_at` in the day; `>= 0` |
| `failures` | `integer` NOT NULL | those of them with status `FAILED`; `>= 0` and `<= runs` |
| `matches_reported` | `integer` NOT NULL | arrivals of the source with `reported_at` in the day; `>= 0` |
| `avg_time_to_report_seconds` | `bigint` NULL | mean time to report of those arrivals; NULL when there are none; `>= 0` |
| `pending_end_of_day` | `integer` NOT NULL | tracked matches still pending at the end of the day; `>= 0` |
| `zone` | `varchar(64)` NOT NULL | the statistics zone the day boundaries were taken in |
| `computed_at` | `timestamptz` NOT NULL | when the row was written |

Definitions (all in `StatisticsRules`, the only place that applies them; days are local days in the zone):

- **Arrival**: a tracked match with `reported_at > first_seen_at`, so its result arrived while it was tracked. A match
  first seen already reported has an unknown arrival time and is left out of every time-to-report figure and of
  `matches_reported`.
- **Time to report**: `reported_at - match_date_time` for arrivals with a match date and `reported_at >= match_date_time`.
- **Pending at end of day**: the match is not ignored, its day is not `CLOSED`, `match_date_time` is before the end of the
  day, it was not reported by then (`reported_at` null or at/after the end of the day) and it is not `POSTPONED`. It is
  computed from the state at aggregation time, so a day aggregated late is an approximation.

Writer: `DailyStatsAggregator` is the only writer. Each day is written once, after it is complete, in one transaction of
three rows (one per source, zero rows included), and a stored day is never recomputed. The daily job (`DailyStatsSchedule`,
under the ShedLock lock `pipeline-daily-stats`) catches up every missing day from the day after the latest stored one
(at most `PIPELINE_STATISTICS_BACKFILL_DAYS` back) through yesterday. Reads: `StatisticsQueries` only.

Indexes added by `V8` for the statistics range reads: `ix_pipeline_run_finished (finished_at)`,
`ix_pipeline_step_finished (finished_at)`, `ix_match_tracking_reported (reported_at)` and
`ix_import_report_received (received_at)`.

## Replay and retention

FEAT-00114, `V9`. No change to `pipeline_run`: `RETRY` and `retry_of_run_id` exist since `V1` and the active-run unique
index covers replays.

- **Replay.** `ReplayRun` is the only creator of `RETRY` runs. A replay run has no `INGEST` or `FETCH_PACKAGE` step and no
  `ingest_run_id`; its status goes `QUEUED -> PACKED -> IMPORTING -> ...` and its start time is the replay start. The
  executor adds a `ZIP` row for the replay run with a new id, the replay `run_id`, the same `storage_key`, `sha256` and
  `size_bytes` as the original and `created_at = now`. The file is shared, never copied.
- **Failure `ARTIFACT_PURGED`.** The replay run fails with it (final, never retried) when the original's `ZIP` row is purged
  or missing, or its file does not exist. It is never `PACKAGE_GONE`, which means ingest lost the package. A stored file
  that no longer matches the recorded `sha256` fails with `PACKAGE_CHECKSUM_MISMATCH`.
- **Platform deduplication.** A replay re-imports only when the platform has no active, `SUCCEEDED` or `PARTIAL` job for the
  same `contentSha256`; otherwise the platform returns the existing job and the `IMPORT` step records
  `import_job_reused = true`.
- **Purge semantics.** `ArtifactCleanup` deletes the file first and then sets `purged_at` on every row of the storage key;
  rows are never deleted. A crash in between leaves an unpurged row whose file is gone: the next pass deletes again
  (idempotent) and marks it. A key is never purged while any run that references it is active.
- **Retention is opt-in** (`tt.pipeline.retention`, see the runtime README). The age of a shared key is that of its oldest
  row, and its source and season are those of the original run.

## Migration history

| Version | File | Content |
| --- | --- | --- |
| `V1` | `V1__pipeline_run_model.sql` | `pipeline_run`, `pipeline_step`, `run_artifact`, `import_report`, active-run partial index |
| `V2` | `V2__manual_triggers.sql` | `pipeline_run.force`, `ix_pipeline_run_created`, `pending_trigger` |
| `V3` | `V3__scheduler_lock.sql` | `shedlock` |
| `V4` | `V4__match_day_tracker.sql` | `match_day`, `match_tracking`, `match_day_event` |
| `V5` | `V5__adaptive_polling.sql` | `poll_schedule`, `poll_policy` |
| `V6` | `V6__match_day_refresh_event.sql` | `match_day_event.kind` CHECK (`match_day_event_kind_check`) also accepts `REFRESH_REQUESTED` |
| `V7` | `V7__alerts.sql` | `alert`, partial unique index `ux_alert_active`, `ix_alert_raised` |
| `V8` | `V8__statistics.sql` | `pipeline_step.http_errors`, `timeouts`, `parse_errors` (+ CHECKs), `import_report.amended_played`, `daily_stats`, `ix_pipeline_run_finished`, `ix_pipeline_step_finished`, `ix_match_tracking_reported`, `ix_import_report_received` |
| `V9` | `V9__replay_and_retention.sql` | `run_artifact.purged_at` (+ CHECK), `ix_run_artifact_storage_key`, `ix_run_artifact_unpurged`, `pipeline_step.import_job_reused` (+ CHECK) |