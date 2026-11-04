-- Match-day refresh (FEAT-00111): an operator can re-ingest one match day; the launch is recorded in its timeline.
-- PostgreSQL names the inline CHECK of V4 match_day_event_kind_check.

ALTER TABLE pipeline.match_day_event DROP CONSTRAINT match_day_event_kind_check;

ALTER TABLE pipeline.match_day_event ADD CONSTRAINT match_day_event_kind_check
    CHECK (kind IN ('OPENED', 'CLOSED', 'REOPENED', 'MATCH_REPORTED', 'MATCH_IGNORED', 'MATCH_UNIGNORED',
                    'MATCH_REMOVED', 'NOTE', 'REFRESH_REQUESTED'));
