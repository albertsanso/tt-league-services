-- Orchestrator run model. Schema 'pipeline' is created by Flyway (create-schemas).
-- No reference to any platform table (decision D2).

CREATE TABLE pipeline.pipeline_run (
    id              uuid         NOT NULL PRIMARY KEY,
    source          varchar(16)  NOT NULL CHECK (source IN ('RFETM', 'BCNESA', 'FCTT')),
    season          varchar(9)   NOT NULL CHECK (season ~ '^[0-9]{4}-[0-9]{4}$'),
    scope           jsonb        NOT NULL,
    trigger         varchar(16)  NOT NULL CHECK (trigger IN ('SCHEDULED', 'MANUAL', 'RETRY')),
    requested_by    varchar(128) NOT NULL,
    retry_of_run_id uuid         NULL REFERENCES pipeline.pipeline_run (id),
    status          varchar(16)  NOT NULL CHECK (status IN ('QUEUED', 'RUNNING_INGEST', 'NO_CHANGES', 'PACKED',
                                                            'IMPORTING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    created_at      timestamptz  NOT NULL,
    started_at      timestamptz  NULL,
    finished_at     timestamptz  NULL,
    ingest_run_id   varchar(64)  NULL,
    import_job_id   uuid         NULL,
    error_code      varchar(64)  NULL,
    error_message   text         NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CHECK ((trigger = 'RETRY') = (retry_of_run_id IS NOT NULL))
);

CREATE UNIQUE INDEX ux_pipeline_run_active_source
    ON pipeline.pipeline_run (source)
    WHERE status IN ('QUEUED', 'RUNNING_INGEST', 'PACKED', 'IMPORTING');
CREATE INDEX ix_pipeline_run_source_created ON pipeline.pipeline_run (source, created_at DESC);
CREATE INDEX ix_pipeline_run_status ON pipeline.pipeline_run (status);

CREATE TABLE pipeline.pipeline_step (
    id            uuid         NOT NULL PRIMARY KEY,
    run_id        uuid         NOT NULL REFERENCES pipeline.pipeline_run (id),
    kind          varchar(16)  NOT NULL CHECK (kind IN ('INGEST', 'FETCH_PACKAGE', 'IMPORT')),
    attempt       int          NOT NULL CHECK (attempt >= 1),
    status        varchar(16)  NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    started_at    timestamptz  NOT NULL,
    finished_at   timestamptz  NULL,
    external_ref  varchar(64)  NULL,
    outcome       varchar(32)  NULL,
    retryable     boolean      NULL,
    error_code    varchar(64)  NULL,
    error_message text         NULL,
    log_ref       varchar(512) NULL,
    UNIQUE (run_id, kind, attempt)
);

CREATE TABLE pipeline.run_artifact (
    id          uuid         NOT NULL PRIMARY KEY,
    run_id      uuid         NOT NULL REFERENCES pipeline.pipeline_run (id),
    kind        varchar(16)  NOT NULL CHECK (kind IN ('ZIP', 'MANIFEST', 'RAW', 'JSON')),
    storage_key varchar(512) NOT NULL,
    sha256      char(64)     NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    size_bytes  bigint       NOT NULL CHECK (size_bytes >= 0),
    created_at  timestamptz  NOT NULL,
    UNIQUE (run_id, kind, storage_key)
);

CREATE TABLE pipeline.import_report (
    run_id                      uuid        NOT NULL PRIMARY KEY REFERENCES pipeline.pipeline_run (id),
    import_job_id               uuid        NOT NULL,
    import_status               varchar(16) NOT NULL CHECK (import_status IN ('SUCCEEDED', 'PARTIAL', 'FAILED')),
    files_seen                  bigint      NOT NULL CHECK (files_seen >= 0),
    items_persisted             bigint      NOT NULL CHECK (items_persisted >= 0),
    skipped                     bigint      NOT NULL CHECK (skipped >= 0),
    processor_failures          bigint      NOT NULL CHECK (processor_failures >= 0),
    scheduled_created           bigint      NOT NULL CHECK (scheduled_created >= 0),
    upgraded_to_played          bigint      NOT NULL CHECK (upgraded_to_played >= 0),
    rescheduled                 bigint      NOT NULL CHECK (rescheduled >= 0),
    partial_actas               bigint      NOT NULL CHECK (partial_actas >= 0),
    invalid_actas               bigint      NOT NULL CHECK (invalid_actas >= 0),
    unresolved_pending_fixtures bigint      NOT NULL CHECK (unresolved_pending_fixtures >= 0),
    issues                      jsonb       NOT NULL,
    raw_report                  jsonb       NOT NULL,
    received_at                 timestamptz NOT NULL
);
