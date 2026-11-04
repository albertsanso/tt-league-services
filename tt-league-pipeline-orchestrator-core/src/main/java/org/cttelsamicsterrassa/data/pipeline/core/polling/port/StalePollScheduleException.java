package org.cttelsamicsterrassa.data.pipeline.core.polling.port;

import java.util.UUID;

/** A poll schedule was changed or removed by another writer, or the same unit was created concurrently. */
public class StalePollScheduleException extends RuntimeException {

    private final UUID scheduleId;

    public StalePollScheduleException(UUID scheduleId, String message) {
        super(message);
        this.scheduleId = scheduleId;
    }

    public UUID scheduleId() {
        return scheduleId;
    }
}
