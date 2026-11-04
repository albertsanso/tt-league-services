-- Run units (FEAT-00117): a run is split into per-scope units that execute their own ingest, fetch and import chain.
--
-- 1. pipeline_unit: one row per unit with its own status, timings, ingest run, import job, error and progress. A full
--    season run has one 'season' unit; a scoped run has one unit per ingest group (unit_key = the SHA-256 identity of the
--    group, byte-identical to poll_schedule.scope_key). Every existing run is backfilled as one unit (ordinal 0): key
--    'season' for a full-season scope, 'legacy' for a stored scope with filters.
-- 2. pipeline_run: the status set shrinks to QUEUED, RUNNING and the terminal statuses (the intermediate statuses live on
--    the unit, active legacy rows are rewritten to RUNNING), UNIT_RETRY joins the triggers with retry_of_unit_id, and the
--    one-active-run-per-source index follows the new active statuses. ingest_run_id and import_job_id stay as nullable
--    legacy columns that are no longer written: the units carry them.
-- 3. pipeline_step, run_artifact and import_report belong to a unit (backfilled to the ordinal 0 unit of their run).
--    Attempts are numbered per unit and kind, and a unit has at most one import report, so import_report is keyed by unit.
-- 4. alert: UNIT_FAILURES joins the alert kinds.
-- The old check constraints were created anonymously by V1 and V7, so they are looked up in the catalog instead of by name.

CREATE TABLE pipeline.pipeline_unit (
    id                  uuid         NOT NULL PRIMARY KEY,
    run_id              uuid         NOT NULL REFERENCES pipeline.pipeline_run (id),
    ordinal             integer      NOT NULL CHECK (ordinal >= 0),
    unit_key            varchar(64)  NOT NULL CHECK (unit_key IN ('season', 'legacy') OR unit_key ~ '^[0-9a-f]{64}$'),
    label               varchar(256) NOT NULL,
    scope               jsonb        NOT NULL,
    status              varchar(16)  NOT NULL CHECK (status IN ('PENDING', 'RUNNING_INGEST', 'PACKED', 'IMPORTING',
                                                                'NO_CHANGES', 'SUCCEEDED', 'PARTIAL', 'FAILED',
                                                                'SKIPPED')),
    started_at          timestamptz  NULL,
    finished_at         timestamptz  NULL,
    ingest_run_id       varchar(64)  NULL,
    import_job_id       uuid         NULL,
    error_code          varchar(64)  NULL,
    error_message       text         NULL,
    progress_step       varchar(16)  NULL CHECK (progress_step IN ('INGEST', 'FETCH_PACKAGE', 'IMPORT')),
    progress_stage      varchar(64)  NULL,
    progress_items      bigint       NULL CHECK (progress_items >= 0),
    progress_total      bigint       NULL CHECK (progress_total >= 0),
    progress_current    varchar(256) NULL,
    progress_updated_at timestamptz  NULL,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_pipeline_unit_run_ordinal UNIQUE (run_id, ordinal),
    -- the aggregate invariants of RunUnit
    CONSTRAINT ck_pipeline_unit_finished CHECK (
        (status IN ('NO_CHANGES', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED')) = (finished_at IS NOT NULL)),
    CONSTRAINT ck_pipeline_unit_started CHECK (
        (status NOT IN ('PENDING', 'SKIPPED') OR started_at IS NULL)
        AND (status IN ('PENDING', 'SKIPPED', 'FAILED') OR started_at IS NOT NULL)),
    CONSTRAINT ck_pipeline_unit_order CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at),
    CONSTRAINT ck_pipeline_unit_error CHECK (
        (status IN ('FAILED', 'SKIPPED')) = (error_code IS NOT NULL) AND (error_code IS NULL) = (error_message IS NULL)),
    CONSTRAINT ck_pipeline_unit_import_job CHECK (
        (status NOT IN ('IMPORTING', 'SUCCEEDED', 'PARTIAL') OR import_job_id IS NOT NULL)
        AND (status NOT IN ('PENDING', 'RUNNING_INGEST', 'PACKED', 'NO_CHANGES', 'SKIPPED') OR import_job_id IS NULL)),
    CONSTRAINT ck_pipeline_unit_progress CHECK (
        (progress_step IS NULL) = (progress_items IS NULL)
        AND (progress_step IS NULL) = (progress_updated_at IS NULL)
        AND (progress_step IS NOT NULL
             OR (progress_stage IS NULL AND progress_total IS NULL AND progress_current IS NULL))
        AND (progress_step IS NULL OR status IN ('RUNNING_INGEST', 'PACKED', 'IMPORTING'))
        AND (progress_total IS NULL OR progress_items <= progress_total))
);

CREATE INDEX ix_pipeline_unit_key_finished ON pipeline.pipeline_unit (unit_key, finished_at DESC);
CREATE INDEX ix_pipeline_unit_finished ON pipeline.pipeline_unit (finished_at);

-- One unit per existing run, with the status of the run (QUEUED reads PENDING).
INSERT INTO pipeline.pipeline_unit (id, run_id, ordinal, unit_key, label, scope, status, started_at, finished_at,
                                    ingest_run_id, import_job_id, error_code, error_message, version)
SELECT gen_random_uuid(), r.id, 0,
       CASE WHEN jsonb_array_length(r.scope -> 'scopes') = 0 THEN 'season' ELSE 'legacy' END,
       CASE WHEN jsonb_array_length(r.scope -> 'scopes') = 0 THEN 'Full season' ELSE 'Legacy scope' END,
       r.scope,
       CASE WHEN r.status = 'QUEUED' THEN 'PENDING' ELSE r.status END,
       r.started_at, r.finished_at, r.ingest_run_id, r.import_job_id, r.error_code, r.error_message, 0
FROM pipeline.pipeline_run r;

-- pipeline_run: drop the anonymous status, trigger and retry checks of V1, then rewrite the active rows.
DO $$
DECLARE
    old_check record;
BEGIN
    FOR old_check IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'pipeline.pipeline_run'::regclass AND contype = 'c'
          AND (pg_get_constraintdef(oid) LIKE '%status%' OR pg_get_constraintdef(oid) LIKE '%trigger%')
    LOOP
        EXECUTE format('ALTER TABLE pipeline.pipeline_run DROP CONSTRAINT %I', old_check.conname);
    END LOOP;
END $$;

DROP INDEX pipeline.ux_pipeline_run_active_source;

UPDATE pipeline.pipeline_run SET status = 'RUNNING' WHERE status IN ('RUNNING_INGEST', 'PACKED', 'IMPORTING');

ALTER TABLE pipeline.pipeline_run
    ADD COLUMN retry_of_unit_id uuid NULL REFERENCES pipeline.pipeline_unit (id),
    ADD CONSTRAINT ck_pipeline_run_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'NO_CHANGES', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    ADD CONSTRAINT ck_pipeline_run_trigger CHECK (trigger IN ('SCHEDULED', 'MANUAL', 'RETRY', 'UNIT_RETRY')),
    ADD CONSTRAINT ck_pipeline_run_retry CHECK (
        ((trigger IN ('RETRY', 'UNIT_RETRY')) = (retry_of_run_id IS NOT NULL))
        AND ((trigger = 'UNIT_RETRY') = (retry_of_unit_id IS NOT NULL)));

CREATE UNIQUE INDEX ux_pipeline_run_active_source
    ON pipeline.pipeline_run (source)
    WHERE status IN ('QUEUED', 'RUNNING');

COMMENT ON COLUMN pipeline.pipeline_run.ingest_run_id IS 'Legacy (before V10), no longer written: see pipeline_unit.ingest_run_id';
COMMENT ON COLUMN pipeline.pipeline_run.import_job_id IS 'Legacy (before V10), no longer written: see pipeline_unit.import_job_id';

-- pipeline_step: attempts are numbered per unit and kind.
ALTER TABLE pipeline.pipeline_step ADD COLUMN unit_id uuid NULL REFERENCES pipeline.pipeline_unit (id);

UPDATE pipeline.pipeline_step s SET unit_id = u.id
FROM pipeline.pipeline_unit u
WHERE u.run_id = s.run_id AND u.ordinal = 0;

ALTER TABLE pipeline.pipeline_step ALTER COLUMN unit_id SET NOT NULL;

DO $$
DECLARE
    old_unique record;
BEGIN
    FOR old_unique IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'pipeline.pipeline_step'::regclass AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (run_id, kind, attempt)'
    LOOP
        EXECUTE format('ALTER TABLE pipeline.pipeline_step DROP CONSTRAINT %I', old_unique.conname);
    END LOOP;
END $$;

ALTER TABLE pipeline.pipeline_step
    ADD CONSTRAINT uq_pipeline_step_unit_kind_attempt UNIQUE (unit_id, kind, attempt);

-- run_artifact: each file belongs to the unit that produced (or, for a replay, shares) it.
ALTER TABLE pipeline.run_artifact ADD COLUMN unit_id uuid NULL REFERENCES pipeline.pipeline_unit (id);

UPDATE pipeline.run_artifact a SET unit_id = u.id
FROM pipeline.pipeline_unit u
WHERE u.run_id = a.run_id AND u.ordinal = 0;

ALTER TABLE pipeline.run_artifact ALTER COLUMN unit_id SET NOT NULL;

CREATE INDEX ix_run_artifact_unit ON pipeline.run_artifact (unit_id);

-- import_report: one report per unit; run_id stays a foreign key with its own index.
ALTER TABLE pipeline.import_report ADD COLUMN unit_id uuid NULL REFERENCES pipeline.pipeline_unit (id);

UPDATE pipeline.import_report i SET unit_id = u.id
FROM pipeline.pipeline_unit u
WHERE u.run_id = i.run_id AND u.ordinal = 0;

ALTER TABLE pipeline.import_report ALTER COLUMN unit_id SET NOT NULL;
ALTER TABLE pipeline.import_report DROP CONSTRAINT import_report_pkey;
ALTER TABLE pipeline.import_report ADD CONSTRAINT import_report_pkey PRIMARY KEY (unit_id);

CREATE INDEX ix_import_report_run ON pipeline.import_report (run_id);

-- alert: the kind check of V7 is anonymous too.
DO $$
DECLARE
    old_check record;
BEGIN
    FOR old_check IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'pipeline.alert'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%MATCH_DAY_CLOSED%'
    LOOP
        EXECUTE format('ALTER TABLE pipeline.alert DROP CONSTRAINT %I', old_check.conname);
    END LOOP;
END $$;

ALTER TABLE pipeline.alert
    ADD CONSTRAINT ck_alert_kind CHECK (
        kind IN ('MATCH_DAY_CLOSED', 'RUN_FAILURES', 'MATCH_UNREPORTED', 'NO_RECENT_SUCCESS', 'UNIT_FAILURES'));
