package org.cttelsamicsterrassa.data.pipeline.runtime.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertEvaluator;
import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertRunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertSettings;
import org.cttelsamicsterrassa.data.pipeline.core.alert.NotifyingPollingAlerts;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.CompositeRunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryAlertRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingNotifier;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.runtime.polling.LoggingPollingAlerts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The runtime wiring without a database or SMTP server: the composite run observer, the {@link AlertRunObserver}, the
 * real {@link AlertDispatcher} thread and the evaluator over in-memory stores, with the fake notifier.
 */
class AlertsIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-05T10:00:00Z");

    private final InMemoryAlertRepository alerts = new InMemoryAlertRepository();
    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final FakeRunClock clock = new FakeRunClock(T0.plus(Duration.ofHours(1)));
    private AlertDispatcher dispatcher;
    private RunObserver observer;

    @BeforeEach
    void wire() {
        AlertSettings settings = new AlertSettings(Duration.ofHours(48), Duration.ofHours(24), Duration.ofDays(1));
        AlertEvaluator evaluator =
                new AlertEvaluator(alerts, new InMemoryMatchDayRepository(), runs, notifier, clock, settings);
        dispatcher = new AlertDispatcher(evaluator, notifier);
        dispatcher.start();
        observer = CompositeRunObserver.of(List.of(new AlertRunObserver(dispatcher)));
    }

    @AfterEach
    void stop() {
        dispatcher.stop();
    }

    private void failedRun(int minutes) throws Exception {
        Instant created = T0.plus(Duration.ofMinutes(minutes));
        PipelineRun failed = PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027",
                RunScope.fullSeason(), false, RunTrigger.SCHEDULED, "system:scheduler", null, created)
                .fail(new RunError("IMPORT_FAILED", "internal detail"), created.plusSeconds(30));
        runs.create(failed);
        observer.runChanged(failed);
        dispatcher.awaitIdle();
    }

    private void succeededRun(int minutes) throws Exception {
        Instant created = T0.plus(Duration.ofMinutes(minutes));
        PipelineRun ok = PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027", RunScope.fullSeason(),
                false, RunTrigger.SCHEDULED, "system:scheduler", null, created)
                .startIngest("ingest", created.plusSeconds(1))
                .noChanges(created.plusSeconds(30));
        runs.create(ok);
        observer.runChanged(ok);
        dispatcher.awaitIdle();
    }

    @Test
    void twoFailedRunsSendOneEmailAndAThirdSendsNothing() throws Exception {
        failedRun(0);
        assertThat(notifier.sent()).isEmpty();

        failedRun(10);
        assertThat(notifier.sent()).singleElement().satisfies(notification -> {
            assertThat(notification.subject()).isEqualTo("FCTT: two consecutive runs failed");
            assertThat(notification.body()).contains("IMPORT_FAILED").doesNotContain("internal detail");
        });

        failedRun(20);
        assertThat(notifier.sent()).hasSize(1);
        assertThat(alerts.findActive()).hasSize(1);
    }

    @Test
    void aSuccessClearsTheAlertAndANewStreakAlertsAgain() throws Exception {
        failedRun(0);
        failedRun(10);
        succeededRun(20);

        assertThat(alerts.findActive()).isEmpty();
        assertThat(notifier.sent()).hasSize(1);

        failedRun(30);
        failedRun(40);

        assertThat(notifier.sent()).hasSize(2);
        assertThat(alerts.findActive()).hasSize(1);
    }

    @Test
    void aFailingSmtpServerKeepsTheAlertForTheNextTrigger() throws Exception {
        notifier.failWith(true);
        failedRun(0);
        failedRun(10);

        assertThat(notifier.sent()).isEmpty();
        assertThat(alerts.findActive()).singleElement().satisfies(alert -> assertThat(alert.notifiedAt()).isNull());

        notifier.failWith(false);
        succeededRun(20);
        failedRun(30);
        failedRun(40);

        // the first alert cleared when the success arrived; the new streak raised a fresh one that went out
        assertThat(notifier.sent()).hasSize(1);
        assertThat(alerts.findActive()).singleElement().satisfies(alert -> assertThat(alert.notifiedAt()).isNotNull());
    }

    @Test
    void pollingAlertsGoThroughTheDispatcherWhileTheLogLinesStay() throws Exception {
        NotifyingPollingAlerts polling = new NotifyingPollingAlerts(new LoggingPollingAlerts(), dispatcher::send);

        polling.scopeUnmatched(PipelineSource.RFETM, "2026-2027", "No ingest status for 2 match days");
        dispatcher.awaitIdle();

        assertThat(notifier.sent()).singleElement().satisfies(notification -> {
            assertThat(notification.subject()).contains("RFETM", "2026-2027");
            assertThat(notification.body()).contains("No ingest status for 2 match days");
        });
        assertThat(alerts.all()).isEmpty();
    }
}
