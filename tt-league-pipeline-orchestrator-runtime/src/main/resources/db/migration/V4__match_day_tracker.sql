-- Match-day tracker (FEAT-00107): tracked platform jornadas, their matches and an append-only timeline.
-- Platform match ids are plain references (no foreign key, no reference to any platform table).

CREATE TABLE pipeline.match_day (
    id                 uuid         NOT NULL PRIMARY KEY,
    source             varchar(16)  NOT NULL CHECK (source IN ('RFETM', 'BCNESA', 'FCTT')),
    season             varchar(9)   NOT NULL CHECK (season ~ '^[0-9]{4}-[0-9]{4}$'),
    competition        varchar(255) NOT NULL,
    group_number       integer      NULL CHECK (group_number >= 1),
    phase              varchar(255) NULL,
    round              integer      NOT NULL CHECK (round >= 1),
    first_date         date         NULL,
    last_date          date         NULL,
    grace_days         integer      NOT NULL CHECK (grace_days >= 0),
    state              varchar(16)  NOT NULL CHECK (state IN ('UPCOMING', 'OPEN', 'CLOSED')),
    close_reason       varchar(16)  NULL CHECK (close_reason IN ('ALL_RESOLVED', 'MANUAL', 'REMOVED')),
    closed_at          timestamptz  NULL,
    closed_by          varchar(128) NULL,
    opened_at          timestamptz  NULL,
    created_at         timestamptz  NOT NULL,
    last_recomputed_at timestamptz  NOT NULL,
    version            bigint       NOT NULL DEFAULT 0,
    CHECK ((first_date IS NULL) = (last_date IS NULL)),
    CHECK (first_date IS NULL OR first_date <= last_date),
    CHECK ((state = 'CLOSED') = (close_reason IS NOT NULL AND closed_at IS NOT NULL AND closed_by IS NOT NULL)),
    CHECK (state = 'CLOSED' OR (close_reason IS NULL AND closed_at IS NULL AND closed_by IS NULL)),
    CHECK (state <> 'OPEN' OR opened_at IS NOT NULL)
);

-- One match day per jornada; a missing group or phase counts as one value (a plain UNIQUE would allow duplicates).
CREATE UNIQUE INDEX ux_match_day_key
    ON pipeline.match_day (source, season, competition, COALESCE(group_number, 0), COALESCE(phase, ''), round);
CREATE INDEX ix_match_day_source_season_state ON pipeline.match_day (source, season, state);
CREATE INDEX ix_match_day_first_date ON pipeline.match_day (first_date);

CREATE TABLE pipeline.match_tracking (
    match_id          uuid         NOT NULL PRIMARY KEY,
    match_day_id      uuid         NOT NULL REFERENCES pipeline.match_day (id),
    status            varchar(16)  NOT NULL CHECK (status IN ('SCHEDULED', 'AWAITING_RESULT', 'REPORTED', 'POSTPONED',
                                                              'OVERDUE')),
    match_date_time   timestamptz  NULL,
    home_team_name    varchar(255) NULL,
    away_team_name    varchar(255) NULL,
    first_seen_at     timestamptz  NOT NULL,
    status_changed_at timestamptz  NOT NULL,
    last_seen_at      timestamptz  NOT NULL,
    reported_at       timestamptz  NULL,
    reported_run_id   uuid         NULL REFERENCES pipeline.pipeline_run (id),
    ignored_at        timestamptz  NULL,
    ignored_by        varchar(128) NULL,
    version           bigint       NOT NULL DEFAULT 0,
    CHECK ((status = 'REPORTED') = (reported_at IS NOT NULL)),
    CHECK (reported_run_id IS NULL OR reported_at IS NOT NULL),
    CHECK ((ignored_at IS NULL) = (ignored_by IS NULL))
);

CREATE INDEX ix_match_tracking_day ON pipeline.match_tracking (match_day_id);

CREATE TABLE pipeline.match_day_event (
    id           uuid          NOT NULL PRIMARY KEY,
    match_day_id uuid          NOT NULL REFERENCES pipeline.match_day (id),
    match_id     uuid          NULL,
    kind         varchar(32)   NOT NULL CHECK (kind IN ('OPENED', 'CLOSED', 'REOPENED', 'MATCH_REPORTED',
                                                        'MATCH_IGNORED', 'MATCH_UNIGNORED', 'MATCH_REMOVED', 'NOTE')),
    actor        varchar(128)  NOT NULL,
    occurred_at  timestamptz   NOT NULL,
    run_id       uuid          NULL REFERENCES pipeline.pipeline_run (id),
    note         varchar(2000) NULL,
    CHECK (kind <> 'NOTE' OR note IS NOT NULL)
);

CREATE INDEX ix_match_day_event_day ON pipeline.match_day_event (match_day_id, occurred_at);
