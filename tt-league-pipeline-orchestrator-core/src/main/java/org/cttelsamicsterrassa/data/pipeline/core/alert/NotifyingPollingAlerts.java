package org.cttelsamicsterrassa.data.pipeline.core.alert;

import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notification;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollingAlerts;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keeps the behaviour of the delegate (the WARN log lines) and also hands a {@link Notification} to a sink. The sink
 * must not block the polling tick; a {@link RuntimeException} from it is logged and swallowed, as the
 * {@link PollingAlerts} contract requires. This is a deliberate broad catch: alerts are side channels.
 */
public final class NotifyingPollingAlerts implements PollingAlerts {

    private static final System.Logger LOG = System.getLogger(NotifyingPollingAlerts.class.getName());

    private final PollingAlerts delegate;
    private final Consumer<Notification> sink;

    public NotifyingPollingAlerts(PollingAlerts delegate, Consumer<Notification> sink) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is required");
        this.sink = Objects.requireNonNull(sink, "sink is required");
    }

    @Override
    public void scopeStopped(PollSchedule schedule) {
        delegate.scopeStopped(schedule);
        deliver(() -> new Notification(
                schedule.source() + " " + schedule.season() + ": polling stopped for " + schedule.scopeKey(),
                "Adaptive polling stopped.\n\nSource: " + schedule.source()
                        + "\nSeason: " + schedule.season()
                        + "\nScope: " + schedule.scopeKey()
                        + "\nReason: " + schedule.stopReason()
                        + "\nStopped at: " + schedule.stoppedAt()
                        + "\n\nResume it through the polling API."));
    }

    @Override
    public void scopeUnmatched(PipelineSource source, String season, String message) {
        delegate.scopeUnmatched(source, season, message);
        deliver(() -> new Notification(
                source + " " + season + ": open match days could not be scoped",
                "Open match days could not be mapped to ingest status rows.\n\nSource: " + source
                        + "\nSeason: " + season + "\n\n" + message));
    }

    private void deliver(Supplier<Notification> notification) {
        try {
            sink.accept(notification.get());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Polling alert notification failed: {0}",
                    e.getClass().getSimpleName());
        }
    }
}
