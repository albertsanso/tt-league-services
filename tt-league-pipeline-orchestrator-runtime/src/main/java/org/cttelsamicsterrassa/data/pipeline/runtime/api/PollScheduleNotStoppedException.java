package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.UUID;

/** Only a stopped poll schedule can be resumed. */
class PollScheduleNotStoppedException extends RuntimeException {

    PollScheduleNotStoppedException(UUID id) {
        super("Poll schedule " + id + " is not stopped");
    }
}
