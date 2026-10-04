package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.UUID;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(UUID id) {
        super("Run " + id + " does not exist");
    }
}
