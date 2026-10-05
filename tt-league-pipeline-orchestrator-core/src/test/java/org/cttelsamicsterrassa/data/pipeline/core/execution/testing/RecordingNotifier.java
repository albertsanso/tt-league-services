package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notification;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.NotificationException;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notifier;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake notifier: records the notifications it delivered and can be switched to fail. */
public class RecordingNotifier implements Notifier {

    private final List<Notification> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;
    private volatile int attempts;

    @Override
    public void send(Notification notification) {
        attempts++;
        if (failing) {
            throw new NotificationException("SMTP unavailable");
        }
        sent.add(notification);
    }

    public List<Notification> sent() {
        return sent;
    }

    /** Send attempts, including the failed ones. */
    public int attempts() {
        return attempts;
    }

    public void failWith(boolean failing) {
        this.failing = failing;
    }
}
