package org.cttelsamicsterrassa.data.pipeline.core.alert.port;

import java.util.Objects;

/** Plain-text notification: a one-line subject of at most 200 characters and a non-blank body. */
public record Notification(String subject, String body) {

    public static final int MAX_SUBJECT = 200;

    public Notification {
        Objects.requireNonNull(subject, "subject is required");
        Objects.requireNonNull(body, "body is required");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (subject.length() > MAX_SUBJECT) {
            throw new IllegalArgumentException("subject must be at most " + MAX_SUBJECT + " characters");
        }
        if (subject.indexOf('\n') >= 0 || subject.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("subject must not contain line breaks");
        }
        if (body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
    }
}
