package org.cttelsamicsterrassa.data.pipeline.core.polling.port;

import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Operator alerts raised by the adaptive polling. Implementations must not throw into the caller. */
public interface PollingAlerts {

    /** A unit stopped because its matches stayed overdue beyond the configured limit. */
    void scopeStopped(PollSchedule schedule);

    /** Open match days could not be mapped to ingest status rows; {@code message} is user-facing. */
    void scopeUnmatched(PipelineSource source, String season, String message);
}
