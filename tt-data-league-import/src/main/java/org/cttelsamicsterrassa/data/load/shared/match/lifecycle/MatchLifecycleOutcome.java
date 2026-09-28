package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

/**
 * What one acta/fixture did to the stored match during the incremental import lifecycle
 * (FEAT-00081, analysis section 4.3).
 */
public enum MatchLifecycleOutcome {

    /** An unpublished/pending fixture was stored as a new SCHEDULED match. */
    SCHEDULED_CREATED,

    /** A complete played acta was stored as a new PLAYED match with its children. */
    PLAYED_CREATED,

    /** A stored SCHEDULED match was upgraded in place to PLAYED, keeping its id. */
    UPGRADED_TO_PLAYED,

    /** A stored SCHEDULED match got a different schedule and was updated. */
    RESCHEDULED,

    /** A pending fixture matched the stored SCHEDULED match; nothing was written. */
    UNCHANGED,

    /** A stored PLAYED match was seen again; it is never rewritten by import. */
    PLAYED_KEPT,

    /** A pending/partial acta arrived for a stored PLAYED match; nothing was written. */
    REGRESSION_REPORTED,

    /** A partial acta was kept scheduled and reported. */
    PARTIAL_REPORTED,

    /** An invalid acta was kept scheduled (or untouched when already played) and reported. */
    INVALID_REPORTED;

    /**
     * Whether this outcome must appear in the run's reported issues rather than only in the
     * outcome counters.
     */
    public boolean isReportable() {
        return this == REGRESSION_REPORTED || this == PARTIAL_REPORTED || this == INVALID_REPORTED;
    }
}
