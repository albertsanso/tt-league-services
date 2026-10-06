package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.UUID;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(UUID id) {
        super("Run " + id + " does not exist");
    }

    /** The unit does not exist, or belongs to another run. */
    public RunNotFoundException(UUID runId, UUID unitId) {
        super("Unit " + unitId + " does not exist in run " + runId);
    }
}
