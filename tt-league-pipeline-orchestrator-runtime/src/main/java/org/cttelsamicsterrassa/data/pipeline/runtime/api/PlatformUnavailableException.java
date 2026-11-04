package org.cttelsamicsterrassa.data.pipeline.runtime.api;

/** The platform could not answer a read-through request; the message is the gateway's own (it carries no secrets). */
public class PlatformUnavailableException extends RuntimeException {

    public PlatformUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
