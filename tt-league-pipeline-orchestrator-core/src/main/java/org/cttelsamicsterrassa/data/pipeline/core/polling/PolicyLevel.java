package org.cttelsamicsterrassa.data.pipeline.core.polling;

/**
 * Urgency level of a poll unit, most urgent first. The declaration order is the urgency order: the most urgent level
 * of a unit's candidate matches wins.
 */
public enum PolicyLevel {
    MATCH_DAY,
    DAY_AFTER,
    DAYS_2_TO_7,
    OPEN,
    OVERDUE,
    STOPPED,
    FULL_REFRESH;

    /** The level whose interval caps the back-off of this one. */
    public PolicyLevel slower() {
        return switch (this) {
            case MATCH_DAY -> DAY_AFTER;
            case DAY_AFTER -> DAYS_2_TO_7;
            case DAYS_2_TO_7 -> OPEN;
            case OPEN, OVERDUE, FULL_REFRESH -> FULL_REFRESH;
            case STOPPED -> STOPPED;
        };
    }
}
