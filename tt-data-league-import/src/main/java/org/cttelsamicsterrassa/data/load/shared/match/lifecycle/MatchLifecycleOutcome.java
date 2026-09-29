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
    INVALID_REPORTED,

    /**
     * The acta's {@code id_partido} and its natural key point at different stored matches (or the
     * natural-key match already keeps another {@code id_partido}); nothing was written
     * (FEAT-00085).
     */
    FIXTURE_IDENTITY_CONFLICT,

    /** An amended acta was detected and the stored PLAYED match was re-applied in place (FEAT-00089). */
    PLAYED_AMENDED,

    /** An amended acta was detected in report mode; nothing was written (FEAT-00089). */
    PLAYED_AMENDMENT_REPORTED;

    /**
     * Whether this outcome must appear in the run's reported issues rather than only in the
     * outcome counters.
     */
    public boolean isReportable() {
        return this == REGRESSION_REPORTED || this == PARTIAL_REPORTED || this == INVALID_REPORTED
                || this == FIXTURE_IDENTITY_CONFLICT || this == PLAYED_AMENDED
                || this == PLAYED_AMENDMENT_REPORTED;
    }

    /**
     * The warning reason to report for an amended-acta outcome (FEAT-00089), or {@code null} for
     * every other outcome, whose reason comes from the classifier.
     */
    public String amendmentReason() {
        return switch (this) {
            case PLAYED_AMENDED -> "amended acta re-applied";
            case PLAYED_AMENDMENT_REPORTED -> "amended acta detected (report mode)";
            default -> null;
        };
    }
}
