package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

/**
 * The write action a {@link MatchLifecyclePlan} asks the {@link MatchLifecycleWriter} to execute
 * (FEAT-00088). The action, not the reported outcome, decides what is written: a PARTIAL or INVALID
 * acta can reschedule a stored SCHEDULED match while its outcome stays a report.
 */
public enum MatchLifecycleAction {

    /** Save a new PLAYED match with its children, built via {@code buildPlayedContent}. */
    CREATE_PLAYED,

    /** Save a new SCHEDULED header, built via {@code buildScheduledMatch}. */
    CREATE_SCHEDULED,

    /** Replace the content of the stored SCHEDULED match in place, keeping its id. */
    UPGRADE_TO_PLAYED,

    /** Update the stored SCHEDULED match's schedule to the plan's merged schedule. */
    UPDATE_SCHEDULE,

    /**
     * Replace the whole content of a stored PLAYED match in place, keeping its id (FEAT-00089). Used
     * when an amended acta is detected and detection runs in write mode.
     */
    REAPPLY_PLAYED,

    /**
     * Write only the source content checksum of a stored PLAYED match (FEAT-00089), adopting the
     * incoming checksum as the baseline when the stored match has none yet.
     */
    RECORD_SOURCE_CHECKSUM,

    /** Nothing is written; the outcome is informational or a report. */
    NONE
}
