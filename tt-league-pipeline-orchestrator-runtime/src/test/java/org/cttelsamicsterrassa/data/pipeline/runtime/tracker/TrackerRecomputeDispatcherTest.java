package org.cttelsamicsterrassa.data.pipeline.runtime.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedPlatformMatchGateway.match;

import java.time.LocalDate;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedPlatformMatchGateway;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayTracker;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.RunRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrackerRecomputeDispatcherTest {

    private static final String SEASON = "2026-2027";
    private static final LocalDate TODAY = LocalDate.parse("2026-10-04");

    private final ScriptedPlatformMatchGateway gateway = new ScriptedPlatformMatchGateway();
    private final InMemoryMatchDayRepository repository = new InMemoryMatchDayRepository();
    private final FakeRunClock clock = new FakeRunClock();
    private final TrackerRecomputeDispatcher dispatcher =
            new TrackerRecomputeDispatcher(new MatchDayTracker(gateway, repository, clock));

    @BeforeEach
    void openJornada() {
        gateway.today(TODAY).jornada("TERCERA", 1, "1a Fase", 1, TODAY, TODAY, true,
                match(UUID.randomUUID(), "TERCERA", 1, "1a Fase", 1, "OVERDUE"));
    }

    @AfterEach
    void stop() {
        dispatcher.stop();
    }

    @Test
    void runsRequestsInOrderOnItsOwnThread() throws Exception {
        dispatcher.start();
        UUID run = UUID.randomUUID();

        dispatcher.request(PipelineSource.FCTT, SEASON, new RunRef(run, clock.now()));
        dispatcher.request(PipelineSource.FCTT, SEASON, null);
        dispatcher.awaitIdle();

        assertThat(repository.applyCalls()).isEqualTo(2);
        assertThat(repository.allEvents()).anyMatch(event -> run.equals(event.runId()));
        assertThat(gateway.calendarReads).hasSize(2);
    }

    @Test
    void aFailedRecomputeIsLoggedAndDroppedAndTheNextOneStillRuns() throws Exception {
        dispatcher.start();
        gateway.failWith(GatewayException.Kind.UNAVAILABLE, 503);
        dispatcher.request(PipelineSource.FCTT, SEASON, null);
        dispatcher.awaitIdle();
        assertThat(repository.applyCalls()).isZero();

        gateway.recover();
        dispatcher.request(PipelineSource.FCTT, SEASON, null);
        dispatcher.awaitIdle();

        assertThat(repository.applyCalls()).isEqualTo(1);
    }

    @Test
    void anInconsistentSnapshotIsDroppedWithoutWriting() throws Exception {
        dispatcher.start();
        gateway.calendarToday(TODAY.plusDays(1));

        dispatcher.request(PipelineSource.FCTT, SEASON, null);
        dispatcher.awaitIdle();

        assertThat(repository.applyCalls()).isZero();
    }

    @Test
    void anUnexpectedFailureDoesNotStopTheWorker() throws Exception {
        dispatcher.start();
        gateway.jornadas.clear();
        gateway.calendars.clear();
        gateway.jornada("TERCERA", 1, "1a Fase", 1, TODAY, TODAY, true,
                match(UUID.randomUUID(), "TERCERA", 1, "1a Fase", 1, "CANCELLED"));
        dispatcher.request(PipelineSource.FCTT, SEASON, null);
        dispatcher.awaitIdle();
        assertThat(repository.applyCalls()).isZero();

        gateway.clear().jornada("TERCERA", 1, "1a Fase", 1, TODAY, TODAY, true,
                match(UUID.randomUUID(), "TERCERA", 1, "1a Fase", 1, "OVERDUE"));
        dispatcher.request(PipelineSource.FCTT, SEASON, null);
        dispatcher.awaitIdle();

        assertThat(repository.applyCalls()).isEqualTo(1);
    }

    @Test
    void requestsBeforeStartAndAfterStopAreRejectedWithoutThrowing() {
        dispatcher.request(PipelineSource.FCTT, SEASON, null);
        assertThat(dispatcher.isRunning()).isFalse();

        dispatcher.start();
        assertThat(dispatcher.isRunning()).isTrue();
        dispatcher.stop();
        dispatcher.request(PipelineSource.FCTT, SEASON, null);

        assertThat(dispatcher.isRunning()).isFalse();
        assertThat(repository.applyCalls()).isZero();
        assertThat(gateway.calendarReads).isEmpty();
    }

    @Test
    void startAndStopAreIdempotent() {
        dispatcher.start();
        dispatcher.start();
        dispatcher.stop();
        dispatcher.stop();

        assertThat(dispatcher.isRunning()).isFalse();
    }
}
