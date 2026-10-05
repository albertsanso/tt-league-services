-- Notifications and alerts (FEAT-00112): one row per raised alert condition. condition_key is the id of the match day
-- or match, or the source name, as a snapshot: there are deliberately no foreign keys, so alerts outlive the rows
-- they describe. At most one uncleared alert exists per (kind, condition_key), which makes raising idempotent.

CREATE TABLE pipeline.alert (
    id              uuid         NOT NULL PRIMARY KEY,
    kind            varchar(32)  NOT NULL
        CHECK (kind IN ('MATCH_DAY_CLOSED', 'RUN_FAILURES', 'MATCH_UNREPORTED', 'NO_RECENT_SUCCESS')),
    condition_key   varchar(255) NOT NULL,
    source          varchar(16)
        CHECK (source IN ('RFETM', 'BCNESA', 'FCTT')),
    season          varchar(16),
    title           varchar(255) NOT NULL,
    detail          text         NOT NULL,
    raised_at       timestamptz  NOT NULL,
    notified_at     timestamptz,
    notify_attempts integer      NOT NULL DEFAULT 0 CHECK (notify_attempts >= 0),
    last_failure    varchar(128),
    cleared_at      timestamptz,
    version         bigint       NOT NULL
);

CREATE UNIQUE INDEX ux_alert_active ON pipeline.alert (kind, condition_key) WHERE cleared_at IS NULL;

CREATE INDEX ix_alert_raised ON pipeline.alert (raised_at);
