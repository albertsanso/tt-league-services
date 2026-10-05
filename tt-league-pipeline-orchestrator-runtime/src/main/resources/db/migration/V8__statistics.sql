-- History, statistics and observability (FEAT-00113).
--
-- 1. pipeline_step: the source health an ingest attempt reported (HTTP errors, timeouts, parse errors summed over its
--    stages). The three columns are all NULL (unknown: an older ingest service, or a step that never finished the
--    ingest run) or all set, and only an INGEST step may carry them.
-- 2. import_report: amended actas the platform re-applied (amendedPlayed). Reports written before this migration read 0,
--    which means "not recorded before V8".
-- 3. daily_stats: one snapshot row per source and local day, written only by DailyStatsAggregator. It has deliberately
--    no foreign keys: it is a snapshot that outlives the rows it was computed from.
-- 4. Indexes for the range reads of the statistics queries.

ALTER TABLE pipeline.pipeline_step
    ADD COLUMN http_errors  bigint NULL CHECK (http_errors >= 0),
    ADD COLUMN timeouts     bigint NULL CHECK (timeouts >= 0),
    ADD COLUMN parse_errors bigint NULL CHECK (parse_errors >= 0),
    ADD CONSTRAINT ck_pipeline_step_health_all_or_none CHECK (
        (http_errors IS NULL) = (timeouts IS NULL) AND (timeouts IS NULL) = (parse_errors IS NULL)),
    ADD CONSTRAINT ck_pipeline_step_health_ingest_only CHECK (http_errors IS NULL OR kind = 'INGEST');

ALTER TABLE pipeline.import_report
    ADD COLUMN amended_played bigint NOT NULL DEFAULT 0 CHECK (amended_played >= 0);

CREATE TABLE pipeline.daily_stats (
    stat_date                  date         NOT NULL,
    source                     varchar(16)  NOT NULL CHECK (source IN ('RFETM', 'BCNESA', 'FCTT')),
    runs                       integer      NOT NULL CHECK (runs >= 0),
    failures                   integer      NOT NULL CHECK (failures >= 0),
    matches_reported           integer      NOT NULL CHECK (matches_reported >= 0),
    avg_time_to_report_seconds bigint       NULL CHECK (avg_time_to_report_seconds >= 0),
    pending_end_of_day         integer      NOT NULL CHECK (pending_end_of_day >= 0),
    zone                       varchar(64)  NOT NULL,
    computed_at                timestamptz  NOT NULL,
    PRIMARY KEY (stat_date, source),
    CHECK (failures <= runs)
);

CREATE INDEX ix_pipeline_run_finished ON pipeline.pipeline_run (finished_at);
CREATE INDEX ix_pipeline_step_finished ON pipeline.pipeline_step (finished_at);
CREATE INDEX ix_match_tracking_reported ON pipeline.match_tracking (reported_at);
CREATE INDEX ix_import_report_received ON pipeline.import_report (received_at);
