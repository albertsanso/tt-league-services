package org.cttelsamicsterrassa.data.pipeline.runtime.events;

import java.util.Objects;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRequests;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells the live views first and then asks for an alert evaluation, so a tracker close or a manual close or reopen
 * is evaluated promptly. Each part is isolated: a failure of one is logged and never stops the other or reaches the
 * caller.
 */
public final class CompositeMatchDayChangeListener implements MatchDayChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(CompositeMatchDayChangeListener.class);

    private final MatchDayChangeListener events;
    private final AlertRequests alerts;

    public CompositeMatchDayChangeListener(MatchDayChangeListener events, AlertRequests alerts) {
        this.events = Objects.requireNonNull(events, "events is required");
        this.alerts = Objects.requireNonNull(alerts, "alerts is required");
    }

    @Override
    public void matchDaysChanged(PipelineSource source, String season, UUID matchDayId, Cause cause) {
        try {
            events.matchDaysChanged(source, season, matchDayId, cause);
        } catch (RuntimeException e) {
            LOG.warn("match-day event listener failed for {} {}: {}", source, season, e.toString());
        }
        try {
            alerts.request();
        } catch (RuntimeException e) {
            LOG.warn("alert request failed for {} {}: {}", source, season, e.toString());
        }
    }
}
