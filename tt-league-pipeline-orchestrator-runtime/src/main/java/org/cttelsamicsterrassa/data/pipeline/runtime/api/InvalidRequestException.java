package org.cttelsamicsterrassa.data.pipeline.runtime.api;

/** A request the API rejects with 400; {@code field} names the offending input when known. */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public InvalidRequestException(String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
