package org.cttelsamicsterrassa.data.pipeline.runtime.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingAlertRequests;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.runtime.events.MatchDayChangeListener.Cause;
import org.junit.jupiter.api.Test;

class CompositeMatchDayChangeListenerTest {

    private final RecordingAlertRequests alerts = new RecordingAlertRequests();
    private final List<String> events = new ArrayList<>();

    @Test
    void tellsTheEventsFirstAndThenRequestsAnEvaluationForEveryCause() {
        CompositeMatchDayChangeListener listener = new CompositeMatchDayChangeListener(
                (source, season, matchDayId, cause) -> events.add(source + ":" + season + ":" + cause), alerts);

        listener.matchDaysChanged(PipelineSource.FCTT, "2026-2027", null, Cause.RECOMPUTED);
        listener.matchDaysChanged(PipelineSource.FCTT, "2026-2027", UUID.randomUUID(), Cause.ACTION);

        assertThat(events).containsExactly("FCTT:2026-2027:RECOMPUTED", "FCTT:2026-2027:ACTION");
        assertThat(alerts.count()).isEqualTo(2);
    }

    @Test
    void aFailingEventListenerStillRequestsTheEvaluation() {
        CompositeMatchDayChangeListener listener = new CompositeMatchDayChangeListener((a, b, c, d) -> {
            throw new IllegalStateException("subscriber gone");
        }, alerts);

        listener.matchDaysChanged(PipelineSource.RFETM, "2026-2027", null, Cause.RECOMPUTED);

        assertThat(alerts.count()).isEqualTo(1);
    }

    @Test
    void aFailingAlertRequestNeverReachesTheCaller() {
        CompositeMatchDayChangeListener listener = new CompositeMatchDayChangeListener(
                (source, season, matchDayId, cause) -> events.add("told"), () -> {
                    throw new IllegalStateException("queue closed");
                });

        listener.matchDaysChanged(PipelineSource.BCNESA, "2026-2027", null, Cause.ACTION);

        assertThat(events).containsExactly("told");
    }
}
