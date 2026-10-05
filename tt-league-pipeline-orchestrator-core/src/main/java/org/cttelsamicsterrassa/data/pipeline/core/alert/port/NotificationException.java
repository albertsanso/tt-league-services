package org.cttelsamicsterrassa.data.pipeline.core.alert.port;

/** A notification could not be delivered; the reason is short and never carries server credentials. */
public class NotificationException extends RuntimeException {

    public NotificationException(String reason) {
        super(reason);
    }

    public NotificationException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
