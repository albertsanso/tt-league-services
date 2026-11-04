package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle state of a {@link RunUnit} with its allowed successors. */
public enum UnitStatus {
    PENDING,
    RUNNING_INGEST,
    PACKED,
    IMPORTING,
    NO_CHANGES,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    SKIPPED;

    private Set<UnitStatus> successors;

    static {
        PENDING.successors = EnumSet.of(RUNNING_INGEST, PACKED, SKIPPED, FAILED);
        RUNNING_INGEST.successors = EnumSet.of(NO_CHANGES, PACKED, FAILED);
        PACKED.successors = EnumSet.of(IMPORTING, FAILED);
        IMPORTING.successors = EnumSet.of(SUCCEEDED, PARTIAL, FAILED);
        NO_CHANGES.successors = EnumSet.noneOf(UnitStatus.class);
        SUCCEEDED.successors = EnumSet.noneOf(UnitStatus.class);
        PARTIAL.successors = EnumSet.noneOf(UnitStatus.class);
        FAILED.successors = EnumSet.noneOf(UnitStatus.class);
        SKIPPED.successors = EnumSet.noneOf(UnitStatus.class);
    }

    public boolean canTransitionTo(UnitStatus next) {
        return successors.contains(next);
    }

    public boolean isTerminal() {
        return successors.isEmpty();
    }

    public boolean isActive() {
        return !isTerminal();
    }

    /** True while the unit is doing work (not waiting for its turn): the statuses that may carry progress. */
    public boolean isRunning() {
        return this == RUNNING_INGEST || this == PACKED || this == IMPORTING;
    }
}
