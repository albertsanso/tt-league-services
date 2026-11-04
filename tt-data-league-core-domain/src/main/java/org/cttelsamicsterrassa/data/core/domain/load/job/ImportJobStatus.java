package org.cttelsamicsterrassa.data.core.domain.load.job;

import java.util.Set;

/**
 * Lifecycle of an import job: {@code QUEUED} -> {@code STORING} -> {@code IMPORTING} -> one of the terminal
 * statuses {@code SUCCEEDED}, {@code PARTIAL} or {@code FAILED}. A job may fail from any active status.
 */
public enum ImportJobStatus {
    QUEUED,
    STORING,
    IMPORTING,
    SUCCEEDED,
    PARTIAL,
    FAILED;

    private static final Set<ImportJobStatus> ACTIVE = Set.of(QUEUED, STORING, IMPORTING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isTerminal() {
        return !isActive();
    }
}
