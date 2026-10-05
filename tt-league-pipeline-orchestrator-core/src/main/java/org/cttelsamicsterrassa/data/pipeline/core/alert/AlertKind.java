package org.cttelsamicsterrassa.data.pipeline.core.alert;

/** Conditions that need operator attention. */
public enum AlertKind {
    MATCH_DAY_CLOSED,
    RUN_FAILURES,
    MATCH_UNREPORTED,
    NO_RECENT_SUCCESS
}
