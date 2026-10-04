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

## Migration history

| Version | File | Content |
| --- | --- | --- |
| `V1` | `V1__pipeline_run_model.sql` | `pipeline_run`, `pipeline_step`, `run_artifact`, `import_report`, active-run partial index |
