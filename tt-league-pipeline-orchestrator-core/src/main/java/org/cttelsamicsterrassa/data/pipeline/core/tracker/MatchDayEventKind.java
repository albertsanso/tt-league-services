package org.cttelsamicsterrassa.data.pipeline.core.tracker;

/** Kind of an entry in a match day's append-only timeline. */
public enum MatchDayEventKind {
    OPENED,
    CLOSED,
    REOPENED,
    MATCH_REPORTED,
    MATCH_IGNORED,
    MATCH_UNIGNORED,
    MATCH_REMOVED,
    NOTE,
    REFRESH_REQUESTED
}
