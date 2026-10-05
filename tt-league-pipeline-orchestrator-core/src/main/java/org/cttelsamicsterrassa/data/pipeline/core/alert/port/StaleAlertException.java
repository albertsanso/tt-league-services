package org.cttelsamicsterrassa.data.pipeline.core.alert.port;

import java.util.UUID;

public class StaleAlertException extends RuntimeException {

    public StaleAlertException(UUID alertId, long expectedVersion) {
        super("Alert " + alertId + " was modified concurrently; expected version " + expectedVersion);
    }
}
