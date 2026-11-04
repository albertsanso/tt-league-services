package org.cttelsamicsterrassa.data.load.shared.preview;

import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecyclePlan;

/**
 * What one previewed fixture would do to the stored matches (FEAT-00088), derived from the shared
 * {@link MatchLifecyclePlan} so the preview and the write path can never disagree. The change is a
 * projection, not a decision: nothing is written and no file is skipped because of it.
 */
public enum PreviewChange {

    /** A new SCHEDULED match would be stored. */
    NEW_SCHEDULED,

    /** A new PLAYED match would be stored. */
    NEW_PLAYED,

    /** A stored SCHEDULED match would be upgraded in place to PLAYED. */
    UPGRADE,

    /** A stored SCHEDULED match would get a new schedule. */
    RESCHEDULE,

    /** The stored match already matches the incoming acta; nothing would change. */
    UNCHANGED,

    /** A stored PLAYED match was seen again; it is never rewritten. */
    PLAYED_KEPT,

    /** A pending/partial acta arrived for a stored PLAYED match; nothing would be written. */
    REGRESSION,

    /** An invalid acta arrived for a stored PLAYED match; nothing would be written. */
    INVALID_ON_PLAYED,

    /** The fixture id and the natural key point at different matches; nothing would be written. */
    IDENTITY_CONFLICT,

    /** The fixture is not stored (early exit or unresolved pending fixture). */
    NOT_STORED;

    /**
     * Derives the change from a plan. {@code UPDATE_SCHEDULE} is always a {@code RESCHEDULE}
     * whatever the reported outcome (a PARTIAL/INVALID acta can reschedule while reporting); the
     * {@code NONE} action is classified by its outcome.
     */
    public static PreviewChange of(MatchLifecyclePlan plan) {
        return switch (plan.action()) {
            case CREATE_SCHEDULED -> NEW_SCHEDULED;
            case CREATE_PLAYED -> NEW_PLAYED;
            case UPGRADE_TO_PLAYED -> UPGRADE;
            case UPDATE_SCHEDULE -> RESCHEDULE;
            case REAPPLY_PLAYED, RECORD_SOURCE_CHECKSUM -> throw new IllegalStateException(
                    "The preview never plans an amended-acta action: " + plan.action());
            case NONE -> switch (plan.outcome()) {
                case REGRESSION_REPORTED -> REGRESSION;
                case INVALID_REPORTED -> INVALID_ON_PLAYED;
                case PLAYED_KEPT -> PLAYED_KEPT;
                case PLAYED_AMENDED, PLAYED_AMENDMENT_REPORTED -> throw new IllegalStateException(
                        "The preview never plans an amended-acta outcome: " + plan.outcome());
                default -> UNCHANGED;
            };
        };
    }

    /** Whether this change adds, moves or rewrites a stored match, so the projection must react. */
    public boolean isStoredChange() {
        return this == NEW_SCHEDULED || this == NEW_PLAYED || this == UPGRADE || this == RESCHEDULE;
    }

    /** Whether this change must be surfaced as a warning to the operator. */
    public boolean isReportable() {
        return this == REGRESSION || this == INVALID_ON_PLAYED || this == IDENTITY_CONFLICT;
    }

    /** The reported outcome used when a change is surfaced as an issue; {@code null} when none. */
    public MatchLifecycleOutcome reportedOutcome() {
        return switch (this) {
            case REGRESSION -> MatchLifecycleOutcome.REGRESSION_REPORTED;
            case INVALID_ON_PLAYED -> MatchLifecycleOutcome.INVALID_REPORTED;
            case IDENTITY_CONFLICT -> MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT;
            default -> null;
        };
    }
}
