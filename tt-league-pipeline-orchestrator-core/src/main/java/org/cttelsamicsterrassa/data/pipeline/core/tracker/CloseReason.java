package org.cttelsamicsterrassa.data.pipeline.core.tracker;

/** Why a match day is closed. */
public enum CloseReason {
    /** Every match is reported, postponed or ignored; the tracker may reopen the day. */
    ALL_RESOLVED,
    /** An operator closed it; only the reopen action reopens it. */
    MANUAL,
    /** The platform no longer reports the jornada. */
    REMOVED
}
