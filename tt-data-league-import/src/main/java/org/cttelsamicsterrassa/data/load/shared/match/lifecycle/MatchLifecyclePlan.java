package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;

/**
 * One fixture's planned lifecycle step (FEAT-00088): the outcome the run reports, the write action
 * the {@link MatchLifecycleWriter} executes, and - only for {@link MatchLifecycleAction#UPDATE_SCHEDULE}
 * - the merged schedule to write.
 */
public record MatchLifecyclePlan(
        MatchLifecycleOutcome outcome,
        MatchLifecycleAction action,
        MatchSchedule mergedSchedule) {

    public MatchLifecyclePlan {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if ((mergedSchedule != null) != (action == MatchLifecycleAction.UPDATE_SCHEDULE)) {
            throw new IllegalArgumentException("mergedSchedule is required for UPDATE_SCHEDULE and forbidden otherwise");
        }
    }

    public static MatchLifecyclePlan of(MatchLifecycleOutcome outcome, MatchLifecycleAction action) {
        return new MatchLifecyclePlan(outcome, action, null);
    }

    public static MatchLifecyclePlan reschedule(MatchLifecycleOutcome outcome, MatchSchedule mergedSchedule) {
        return new MatchLifecyclePlan(outcome, MatchLifecycleAction.UPDATE_SCHEDULE, mergedSchedule);
    }
}
