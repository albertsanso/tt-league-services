package org.cttelsamicsterrassa.data.pipeline.core.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notification;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingPollingAlerts;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PolicyLevel;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotifyingPollingAlertsTest {

    private static final Instant T0 = Instant.parse("2026-10-05T10:00:00Z");

    private final RecordingPollingAlerts delegate = new RecordingPollingAlerts();
    private final List<Notification> sunk = new ArrayList<>();
    private final NotifyingPollingAlerts alerts = new NotifyingPollingAlerts(delegate, sunk::add);

    private static PollSchedule stopped() {
        return PollSchedule.restore(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027", "TERCERA-1",
                new ScopeFilter("TERCERA", "1", null, null, null, List.of()), PolicyLevel.STOPPED,
                Duration.ofHours(1), 0, null, null, null, T0, "matches overdue for too long", null, 0L);
    }

    @Test
    void scopeStoppedCallsTheDelegateAndNotifies() {
        PollSchedule schedule = stopped();

        alerts.scopeStopped(schedule);

        assertThat(delegate.stopped).containsExactly(schedule);
        assertThat(sunk).singleElement().satisfies(notification -> {
            assertThat(notification.subject()).contains("FCTT", "2026-2027", "TERCERA-1");
            assertThat(notification.body()).contains("matches overdue for too long", T0.toString(), "polling API");
        });
    }

    @Test
    void scopeUnmatchedCallsTheDelegateAndNotifies() {
        alerts.scopeUnmatched(PipelineSource.RFETM, "2026-2027", "No ingest status for 2 match days");

        assertThat(delegate.unmatched).hasSize(1);
        assertThat(sunk).singleElement().satisfies(notification -> {
            assertThat(notification.subject()).contains("RFETM", "2026-2027");
            assertThat(notification.body()).contains("No ingest status for 2 match days");
        });
    }

    @Test
    void aFailingSinkIsSwallowedAfterTheDelegateRan() {
        NotifyingPollingAlerts failing = new NotifyingPollingAlerts(delegate, notification -> {
            throw new IllegalStateException("queue closed");
        });

        failing.scopeStopped(stopped());
        failing.scopeUnmatched(PipelineSource.RFETM, "2026-2027", "message");

        assertThat(delegate.stopped).hasSize(1);
        assertThat(delegate.unmatched).hasSize(1);
    }
}
