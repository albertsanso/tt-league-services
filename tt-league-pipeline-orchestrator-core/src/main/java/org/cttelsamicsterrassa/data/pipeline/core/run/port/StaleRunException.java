package org.cttelsamicsterrassa.data.pipeline.core.run.port;

import java.util.UUID;

public class StaleRunException extends RuntimeException {

    private final UUID runId;
    private final long expectedVersion;

    public StaleRunException(UUID runId, long expectedVersion) {
        super("Run " + runId + " was modified concurrently; expected version " + expectedVersion);
        this.runId = runId;
        this.expectedVersion = expectedVersion;
    }

    /** A concurrent change to one unit of the run. */
    public StaleRunException(UUID runId, UUID unitId, long expectedVersion) {
        super("Unit " + unitId + " of run " + runId + " was modified concurrently; expected version "
                + expectedVersion);
        this.runId = runId;
        this.expectedVersion = expectedVersion;
    }

    public UUID runId() {
        return runId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}
