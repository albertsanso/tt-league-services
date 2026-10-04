package org.cttelsamicsterrassa.data.pipeline.core.tracker;

/** Lifecycle of a tracked match day. */
public enum MatchDayState {
    /** Window not started yet, or undated, and nothing reported. */
    UPCOMING,
    OPEN,
    CLOSED
}
