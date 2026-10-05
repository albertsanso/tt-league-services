package org.cttelsamicsterrassa.data.pipeline.runtime.polling;

import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollingAlerts;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Alerts as WARN log lines until notification channels exist (FEAT-00112); never logs secrets. */
public final class LoggingPollingAlerts implements PollingAlerts {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingPollingAlerts.class);

    @Override
    public void scopeStopped(PollSchedule schedule) {
        LOG.warn("adaptive polling stopped for {} {} unit {} ({}): matches overdue beyond the limit; resume it"
                + " through the polling API once handled", schedule.source(), schedule.season(),
                schedule.scopeKey(), schedule.stopReason());
    }

    @Override
    public void scopeUnmatched(PipelineSource source, String season, String message) {
        LOG.warn("adaptive polling for {} {} cannot build its scope: {}", source, season, message);
    }
}
