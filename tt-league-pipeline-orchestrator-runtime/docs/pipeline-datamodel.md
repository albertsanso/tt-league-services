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
| `QUEUED` | `RUNNING_INGEST`, `FAILED` |
| `RUNNING_INGEST` | `NO_CHANGES`, `PACKED`, `FAILED` |
| `PACKED` | `IMPORTING`, `FAILED` |
| `IMPORTING` | `SUCCEEDED`, `PARTIAL`, `FAILED` |
| `NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED` | none (terminal) |

Enforced by `RunStatus` in the core, not by the database.

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

`UNIQUE (run_id, kind, storage_key)`.

## `import_report`

Result of the platform import job of a run; at most one per run.

| Column | Type | Notes |
| --- | --- | --- |
| `run_id` | `uuid` PK | FK `pipeline_run(id)` |
| `import_job_id` | `uuid` NOT NULL | platform job (plain column) |
| `import_status` | `varchar(16)` NOT NULL | `SUCCEEDED`, `PARTIAL`, `FAILED` |
| `files_seen`, `items_persisted`, `skipped`, `processor_failures`, `scheduled_created`, `upgraded_to_played`, `rescheduled`, `partial_actas`, `invalid_actas`, `unresolved_pending_fixtures` | `bigint` NOT NULL | counters summed over the job's seasons, each `>= 0` |
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
  `PACKAGE_CHECKSUM_MISMATCH`, `ARTIFACT_STORE_FAILED`, `PLATFORM_UNAVAILABLE`,
  `IMPORT_REJECTED`, `IMPORT_SHRINK`, `IMPORT_JOB_LOST`, `IMPORT_FAILED`,
  `STEP_TIMEOUT`, `PROTOCOL_ERROR`, `INTERRUPTED`, `DISPATCH_FAILED`,
  `INTERNAL_ERROR`. Messages never contain keys.
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

ShedLock JDBC lock table for the fixed-schedule trigger: one row per lock name, written only by ShedLock
(`JdbcTemplateLockProvider` with the database clock). Lock names are `pipeline-schedule-<SOURCE>`; rows are
reused, not deleted, when a lock is released.

| Column | Type | Notes |
| --- | --- | --- |
| `name` | `varchar(64)` PK | lock name |
| `lock_until` | `timestamp(3)` NOT NULL | UTC database time until which the lock is held |
| `locked_at` | `timestamp(3)` NOT NULL | UTC database time the lock was taken |
| `locked_by` | `varchar(255)` NOT NULL | host name of the instance that took it |

## Migration history

| Version | File | Content |
| --- | --- | --- |
| `V1` | `V1__pipeline_run_model.sql` | `pipeline_run`, `pipeline_step`, `run_artifact`, `import_report`, active-run partial index |
| `V2` | `V2__manual_triggers.sql` | `pipeline_run.force`, `ix_pipeline_run_created`, `pending_trigger` |
| `V3` | `V3__scheduler_lock.sql` | `shedlock` |
