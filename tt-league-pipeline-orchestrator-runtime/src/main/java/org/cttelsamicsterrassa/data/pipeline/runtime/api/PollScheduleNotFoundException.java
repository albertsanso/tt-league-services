package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.UUID;

/** No poll schedule has this id. */
class PollScheduleNotFoundException extends RuntimeException {

    PollScheduleNotFoundException(UUID id) {
        super("Poll schedule not found: " + id);
    }
}
