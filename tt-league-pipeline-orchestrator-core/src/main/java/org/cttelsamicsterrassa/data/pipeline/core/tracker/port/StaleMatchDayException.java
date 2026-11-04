package org.cttelsamicsterrassa.data.pipeline.core.tracker.port;

/** A match day or match changed since it was read, or another instance created the same match day. */
public class StaleMatchDayException extends RuntimeException {

    public StaleMatchDayException(String message) {
        super(message);
    }

    public StaleMatchDayException(String message, Throwable cause) {
        super(message, cause);
    }
}
