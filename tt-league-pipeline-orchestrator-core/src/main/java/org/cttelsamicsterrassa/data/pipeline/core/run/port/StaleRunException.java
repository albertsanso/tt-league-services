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

    public UUID runId() {
        return runId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}
