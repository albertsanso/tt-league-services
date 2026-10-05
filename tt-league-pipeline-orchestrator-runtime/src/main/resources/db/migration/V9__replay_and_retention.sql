-- Replay and artifact retention (FEAT-00114).
--
-- 1. run_artifact.purged_at: set when the retention cleanup deleted the file. The row is kept as history. Replay runs
--    carry their own ZIP row with the same storage_key as the original, so storage_key is indexed (several rows may
--    share one key; (run_id, kind, storage_key) stays unique).
-- 2. pipeline_step.import_job_reused: whether the platform answered a submit with an existing job for the same
--    content (HTTP 200) instead of a new one (HTTP 202). NULL means "not recorded" (steps written before V9) and is
--    the only value on non-IMPORT steps.
-- No change to pipeline_run: RETRY and retry_of_run_id exist since V1 and the active-run unique index covers replays.

ALTER TABLE pipeline.run_artifact
    ADD COLUMN purged_at timestamptz NULL,
    ADD CONSTRAINT ck_run_artifact_purged_after_created CHECK (purged_at IS NULL OR purged_at >= created_at);

CREATE INDEX ix_run_artifact_storage_key ON pipeline.run_artifact (storage_key);
CREATE INDEX ix_run_artifact_unpurged ON pipeline.run_artifact (created_at) WHERE purged_at IS NULL;

ALTER TABLE pipeline.pipeline_step
    ADD COLUMN import_job_reused boolean NULL,
    ADD CONSTRAINT ck_pipeline_step_reused_import_only CHECK (import_job_reused IS NULL OR kind = 'IMPORT');
