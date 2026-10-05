-- Adaptive polling (FEAT-00108): per-unit poll schedules and per-source policy overrides.
-- Poll units are ingest groups (identified by scope_key, a SHA-256 hex of the unit identity without its match days)
-- plus one FULL_REFRESH unit per source and season.

CREATE TABLE pipeline.poll_schedule (
    id                    uuid         NOT NULL PRIMARY KEY,
    source                varchar(16)  NOT NULL CHECK (source IN ('RFETM', 'BCNESA', 'FCTT')),
    season                varchar(9)   NOT NULL CHECK (season ~ '^[0-9]{4}-[0-9]{4}$'),
    scope_key             varchar(64)  NOT NULL,
    kind                  varchar(16)  NOT NULL CHECK (kind IN ('FULL_REFRESH', 'GROUP')),
    filter                jsonb        NULL,
    policy_level          varchar(16)  NOT NULL CHECK (policy_level IN ('MATCH_DAY', 'DAY_AFTER', 'DAYS_2_TO_7', 'OPEN',
                                                                       'OVERDUE', 'STOPPED', 'FULL_REFRESH')),
    interval_seconds      bigint       NULL CHECK (interval_seconds > 0),
    consecutive_no_change integer      NOT NULL DEFAULT 0 CHECK (consecutive_no_change >= 0),
    next_run_at           timestamptz  NULL,
    last_run_at           timestamptz  NULL,
    pending_run_id        uuid         NULL REFERENCES pipeline.pipeline_run (id) ON DELETE SET NULL,
    stopped_at            timestamptz  NULL,
    stop_reason           varchar(32)  NULL,
    alerted_at            timestamptz  NULL,
    version               bigint       NOT NULL,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    CHECK ((kind = 'FULL_REFRESH') = (filter IS NULL)),
    CHECK ((kind = 'FULL_REFRESH') = (scope_key = 'FULL_REFRESH')),
    CHECK ((kind = 'FULL_REFRESH') = (policy_level = 'FULL_REFRESH')),
    CHECK ((policy_level = 'STOPPED') = (stopped_at IS NOT NULL)),
    CHECK ((stopped_at IS NULL) = (stop_reason IS NULL)),
    CHECK (policy_level = 'STOPPED' OR interval_seconds IS NOT NULL)
);

CREATE UNIQUE INDEX ux_poll_schedule_unit ON pipeline.poll_schedule (source, season, scope_key);
CREATE INDEX ix_poll_schedule_next_run ON pipeline.poll_schedule (source, season, next_run_at);

CREATE TABLE pipeline.poll_policy (
    source     varchar(16)  NOT NULL PRIMARY KEY CHECK (source IN ('RFETM', 'BCNESA', 'FCTT')),
    settings   jsonb        NOT NULL,
    version    bigint       NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamptz  NOT NULL
);
