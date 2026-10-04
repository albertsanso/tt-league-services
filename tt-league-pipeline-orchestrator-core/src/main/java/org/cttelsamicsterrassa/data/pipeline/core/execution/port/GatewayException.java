package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.util.Objects;

/** Failure of a call to the ingest service or the platform. The message never carries keys or header values. */
public class GatewayException extends RuntimeException {

    /** Classification the executor's retry rule works on. */
    public enum Kind {
        /** HTTP 5xx, connection or read failure. */
        UNAVAILABLE,
        NOT_FOUND,
        CONFLICT,
        /** Any other 4xx. */
        REJECTED,
        /** Unexpected status, body or header. */
        PROTOCOL
    }

    private final Kind kind;
    private final Integer httpStatus;

    public GatewayException(Kind kind, Integer httpStatus, String message) {
        this(kind, httpStatus, message, null);
    }

    public GatewayException(Kind kind, Integer httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind is required");
        this.httpStatus = httpStatus;
    }

    public Kind kind() {
        return kind;
    }

    /** The HTTP status, or null when the failure happened before a response. */
    public Integer httpStatus() {
        return httpStatus;
    }
}
