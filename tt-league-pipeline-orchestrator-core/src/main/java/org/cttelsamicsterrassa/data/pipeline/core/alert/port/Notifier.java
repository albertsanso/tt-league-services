package org.cttelsamicsterrassa.data.pipeline.core.alert.port;

/**
 * Delivers one operator notification. Implementations may block; callers send off the run, tracker and request
 * threads.
 */
public interface Notifier {

    /** Throws {@link NotificationException} when the notification could not be delivered. */
    void send(Notification notification);
}
