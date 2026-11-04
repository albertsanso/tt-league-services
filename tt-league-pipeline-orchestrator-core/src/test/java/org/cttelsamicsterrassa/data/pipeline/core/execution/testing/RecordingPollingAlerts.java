package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import java.util.ArrayList;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollingAlerts;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Records the alerts raised by the adaptive polling. */
public class RecordingPollingAlerts implements PollingAlerts {

    public final List<PollSchedule> stopped = new ArrayList<>();
    public final List<String> unmatched = new ArrayList<>();

    @Override
    public void scopeStopped(PollSchedule schedule) {
        stopped.add(schedule);
    }

    @Override
    public void scopeUnmatched(PipelineSource source, String season, String message) {
        unmatched.add(source + "/" + season + ": " + message);
    }
}
