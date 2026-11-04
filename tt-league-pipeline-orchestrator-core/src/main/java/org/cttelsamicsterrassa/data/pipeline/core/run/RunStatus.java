package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle state of a {@link PipelineRun} with its allowed successors. Only {@code RunOutcomeRules} derives the
 * terminal status, from the run's units; the intermediate steps live on {@link UnitStatus}.
 */
public enum RunStatus {
    QUEUED,
    RUNNING,
    NO_CHANGES,
    SUCCEEDED,
    PARTIAL,
    FAILED;

    private Set<RunStatus> successors;

    static {
        QUEUED.successors = EnumSet.of(RUNNING, FAILED);
        RUNNING.successors = EnumSet.of(NO_CHANGES, SUCCEEDED, PARTIAL, FAILED);
        NO_CHANGES.successors = EnumSet.noneOf(RunStatus.class);
        SUCCEEDED.successors = EnumSet.noneOf(RunStatus.class);
        PARTIAL.successors = EnumSet.noneOf(RunStatus.class);
        FAILED.successors = EnumSet.noneOf(RunStatus.class);
    }

    public boolean canTransitionTo(RunStatus next) {
        return successors.contains(next);
    }

    public boolean isTerminal() {
        return successors.isEmpty();
    }

    public boolean isActive() {
        return !isTerminal();
    }

    public static Set<RunStatus> active() {
        Set<RunStatus> active = EnumSet.noneOf(RunStatus.class);
        for (RunStatus status : values()) {
            if (status.isActive()) {
                active.add(status);
            }
        }
        return active;
    }
}
