-- Manual triggers (FEAT-00105): forced runs, run listing index and the per-source pending trigger.

ALTER TABLE pipeline.pipeline_run ADD COLUMN force boolean NOT NULL DEFAULT false;

CREATE INDEX ix_pipeline_run_created ON pipeline.pipeline_run (created_at DESC, id);

CREATE TABLE pipeline.pending_trigger (
    source       varchar(16)  NOT NULL PRIMARY KEY CHECK (source IN ('RFETM', 'BCNESA', 'FCTT')),
    season       varchar(9)   NOT NULL CHECK (season ~ '^[0-9]{4}-[0-9]{4}$'),
    scope_type   varchar(16)  NOT NULL CHECK (scope_type IN ('OPEN_MATCH_DAYS', 'GROUP', 'FULL_SEASON')),
    filters      jsonb        NOT NULL,
    force        boolean      NOT NULL,
    requested_by varchar(128) NOT NULL,
    requested_at timestamptz  NOT NULL
);
