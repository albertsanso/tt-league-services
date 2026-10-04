package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPendingTriggerRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingPendingTriggerEvents;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.StubOpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Command;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.ScopeUnavailableException;
import org.junit.jupiter.api.Test;

class TriggerRunTest {

    private static final ScopeFilter FILTER = new ScopeFilter("CAT", "G1", null, null, null, List.of(3));
    private static final RunError ERROR = new RunError("E", "boom");

    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final InMemoryPendingTriggerRepository pending = new InMemoryPendingTriggerRepository();
    private final StubOpenMatchDayScopeResolver resolver = new StubOpenMatchDayScopeResolver();
    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private final RecordingObserver observer = new RecordingObserver();
    private final RecordingPendingTriggerEvents events = new RecordingPendingTriggerEvents();
    private final FakeRunClock clock = new FakeRunClock();
    private final RunLauncher launcher = new RunLauncher(runs, dispatcher, clock, observer);
    private final TriggerRun triggerRun = new TriggerRun(runs, pending, resolver, launcher, events, clock);

    private static Command command(List<PipelineSource> sources, ScopeType type, List<ScopeFilter> filters,
            boolean force, ConflictMode mode) {
        return new Command(sources, "2025-2026", type, filters, force, RunTrigger.MANUAL, "alice", mode);
    }

    private static Command full(List<PipelineSource> sources, ConflictMode mode) {
        return command(sources, ScopeType.FULL_SEASON, List.of(), false, mode);
    }

    private PipelineRun activeRun(PipelineSource source) {
        return runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2025-2026", RunScope.fullSeason(), false,
                RunTrigger.SCHEDULED, "system:scheduler", null, clock.now()));
    }

    @Test
    void singleSourceCreatesAManualRunWithRequesterAndForce() {
        List<Outcome> outcomes = triggerRun.trigger(
                command(List.of(PipelineSource.BCNESA), ScopeType.GROUP, List.of(FILTER), true, ConflictMode.REJECT));

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Created.class, created -> {
            PipelineRun run = created.run();
            assertThat(run.source()).isEqualTo(PipelineSource.BCNESA);
            assertThat(run.trigger()).isEqualTo(RunTrigger.MANUAL);
            assertThat(run.requestedBy()).isEqualTo("alice");
            assertThat(run.force()).isTrue();
            assertThat(run.scope().filters()).containsExactly(FILTER);
            assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        });
        assertThat(dispatcher.dispatched).hasSize(1);
    }

    @Test
    void allCreatesOneRunPerSourceInSourceOrder() {
        List<Outcome> outcomes = triggerRun.trigger(full(List.of(PipelineSource.values()), ConflictMode.REJECT));

        assertThat(outcomes).extracting(Outcome::source).containsExactly(PipelineSource.values());
        assertThat(outcomes).allSatisfy(o -> assertThat(o).isInstanceOf(Outcome.Created.class));
    }

    @Test
    void allWithOneActiveSourceMixesCreatedAndRejected() {
        PipelineRun active = activeRun(PipelineSource.BCNESA);

        List<Outcome> outcomes = triggerRun.trigger(full(List.of(PipelineSource.values()), ConflictMode.REJECT));

        assertThat(outcomes).hasSize(PipelineSource.values().length);
        assertThat(outcomes).filteredOn(o -> o.source() == PipelineSource.BCNESA).singleElement()
                .isInstanceOfSatisfying(Outcome.Rejected.class, rejected -> {
                    assertThat(rejected.code()).isEqualTo("ACTIVE_RUN");
                    assertThat(rejected.activeRunId()).isEqualTo(active.id());
                    assertThat(rejected.message()).contains("BCNESA", active.id().toString(), "QUEUED");
                });
        assertThat(outcomes).filteredOn(o -> o instanceof Outcome.Created)
                .hasSize(PipelineSource.values().length - 1);
    }

    @Test
    void openMatchDaysUsesTheResolvedScope() {
        resolver.returning(new RunScope(List.of(FILTER)));

        List<Outcome> outcomes = triggerRun.trigger(command(List.of(PipelineSource.RFETM),
                ScopeType.OPEN_MATCH_DAYS, List.of(), false, ConflictMode.REJECT));

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Created.class,
                created -> assertThat(created.run().scope().filters()).containsExactly(FILTER));
        assertThat(resolver.resolved).containsExactly(PipelineSource.RFETM);
    }

    @Test
    void unavailableScopeIsReportedWithoutCreatingARun() {
        resolver.failingWith(new ScopeUnavailableException("SCOPE_UNAVAILABLE", "not deployed"));

        List<Outcome> outcomes = triggerRun.trigger(command(List.of(PipelineSource.RFETM),
                ScopeType.OPEN_MATCH_DAYS, List.of(), false, ConflictMode.REJECT));

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Unavailable.class, unavailable -> {
            assertThat(unavailable.code()).isEqualTo("SCOPE_UNAVAILABLE");
            assertThat(unavailable.message()).isEqualTo("not deployed");
        });
        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void queueModeStoresTheRequestNotTheResolvedScope() {
        PipelineRun active = activeRun(PipelineSource.FCTT);
        resolver.returning(new RunScope(List.of(FILTER)));

        List<Outcome> outcomes = triggerRun.trigger(command(List.of(PipelineSource.FCTT),
                ScopeType.OPEN_MATCH_DAYS, List.of(), true, ConflictMode.QUEUE));

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Queued.class, queued -> {
            assertThat(queued.activeRunId()).isEqualTo(active.id());
            assertThat(queued.pending().scopeType()).isEqualTo(ScopeType.OPEN_MATCH_DAYS);
            assertThat(queued.pending().force()).isTrue();
            assertThat(queued.pending().requestedBy()).isEqualTo("alice");
        });
        assertThat(pending.findAll()).singleElement().satisfies(stored -> assertThat(stored.filters()).isEmpty());
        assertThat(events.events).containsExactly("queued:FCTT");
    }

    @Test
    void aSecondPendingTriggerForTheSourceIsRejected() {
        activeRun(PipelineSource.FCTT);
        triggerRun.trigger(full(List.of(PipelineSource.FCTT), ConflictMode.QUEUE));

        List<Outcome> outcomes = triggerRun.trigger(full(List.of(PipelineSource.FCTT), ConflictMode.QUEUE));

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Rejected.class,
                rejected -> assertThat(rejected.code()).isEqualTo("PENDING_EXISTS"));
        assertThat(pending.findAll()).hasSize(1);
    }

    @Test
    void queueModeLaunchesAtOnceWhenTheActiveRunEndedBeforeTheInsert() {
        PipelineRun active = activeRun(PipelineSource.RFETM);
        // The active run ends right after the pending trigger is announced, before the "still active?" check.
        RecordingPendingTriggerEvents endingRun = new RecordingPendingTriggerEvents() {
            @Override
            public void queued(PendingTrigger trigger) {
                super.queued(trigger);
                runs.update(active.fail(ERROR, clock.now()));
            }
        };
        TriggerRun racing = new TriggerRun(runs, pending, resolver, launcher, endingRun, clock);

        List<Outcome> outcomes = racing.trigger(full(List.of(PipelineSource.RFETM), ConflictMode.QUEUE));

        assertThat(outcomes).singleElement().isInstanceOf(Outcome.Queued.class);
        assertThat(pending.findAll()).isEmpty();
        assertThat(runs.findActiveBySource(PipelineSource.RFETM)).get().extracting(PipelineRun::id)
                .isNotEqualTo(active.id());
        assertThat(endingRun.events).containsExactly("queued:RFETM", "launched:RFETM");
    }

    @Test
    void launchPendingLaunchesAndRemovesTheTrigger() {
        PipelineRun active = activeRun(PipelineSource.BCNESA);
        triggerRun.trigger(command(List.of(PipelineSource.BCNESA), ScopeType.GROUP, List.of(FILTER), true,
                ConflictMode.QUEUE));
        runs.update(active.fail(ERROR, clock.now()));

        PipelineRun launched = triggerRun.launchPending(PipelineSource.BCNESA).orElseThrow();

        assertThat(launched.trigger()).isEqualTo(RunTrigger.MANUAL);
        assertThat(launched.requestedBy()).isEqualTo("alice");
        assertThat(launched.force()).isTrue();
        assertThat(launched.scope().filters()).containsExactly(FILTER);
        assertThat(pending.findAll()).isEmpty();
        assertThat(events.events).containsExactly("queued:BCNESA", "launched:BCNESA");
    }

    @Test
    void launchPendingPutsTheTriggerBackOnConflict() {
        activeRun(PipelineSource.BCNESA);
        triggerRun.trigger(full(List.of(PipelineSource.BCNESA), ConflictMode.QUEUE));

        assertThat(triggerRun.launchPending(PipelineSource.BCNESA)).isEmpty();

        assertThat(pending.findAll()).hasSize(1);
    }

    @Test
    void launchPendingDropsATriggerWhoseScopeIsUnavailable() {
        PipelineRun active = activeRun(PipelineSource.RFETM);
        resolver.returning(new RunScope(List.of(FILTER)));
        triggerRun.trigger(command(List.of(PipelineSource.RFETM), ScopeType.OPEN_MATCH_DAYS, List.of(), false,
                ConflictMode.QUEUE));
        runs.update(active.fail(ERROR, clock.now()));
        resolver.failingWith(new ScopeUnavailableException("NO_OPEN_MATCH_DAYS", "nothing open"));

        assertThat(triggerRun.launchPending(PipelineSource.RFETM)).isEmpty();

        assertThat(pending.findAll()).isEmpty();
        assertThat(events.events).containsExactly("queued:RFETM", "dropped:RFETM:NO_OPEN_MATCH_DAYS");
    }

    @Test
    void aFailingEventListenerNeverBreaksTheTrigger() {
        activeRun(PipelineSource.RFETM);
        events.failWith(new IllegalStateException("listener down"));

        List<Outcome> outcomes = triggerRun.trigger(full(List.of(PipelineSource.RFETM), ConflictMode.QUEUE));

        assertThat(outcomes).singleElement().isInstanceOf(Outcome.Queued.class);
    }

    @Test
    void drainIdleLaunchesOnlySourcesWithoutAnActiveRun() {
        PipelineRun stillActive = activeRun(PipelineSource.RFETM);
        PipelineRun ended = activeRun(PipelineSource.FCTT);
        triggerRun.trigger(full(List.of(PipelineSource.RFETM), ConflictMode.QUEUE));
        triggerRun.trigger(full(List.of(PipelineSource.FCTT), ConflictMode.QUEUE));
        runs.update(ended.fail(ERROR, clock.now()));

        assertThat(triggerRun.drainIdle()).isEqualTo(1);

        assertThat(pending.findAll()).extracting(PendingTrigger::source).containsExactly(PipelineSource.RFETM);
        assertThat(runs.findActiveBySource(PipelineSource.RFETM)).get().extracting(PipelineRun::id)
                .isEqualTo(stillActive.id());
    }

    @Test
    void commandValidation() {
        List<PipelineSource> one = List.of(PipelineSource.RFETM);
        List<PipelineSource> all = List.of(PipelineSource.values());
        ConflictMode mode = ConflictMode.REJECT;
        assertThatThrownBy(() -> full(List.of(), mode)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> full(List.of(PipelineSource.RFETM, PipelineSource.RFETM), mode))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Command(one, "2025/2026", ScopeType.FULL_SEASON, List.of(), false,
                RunTrigger.MANUAL, "a", mode)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Command(one, "2025-2027", ScopeType.FULL_SEASON, List.of(), false,
                RunTrigger.MANUAL, "a", mode)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(one, ScopeType.GROUP, List.of(), false, mode))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(all, ScopeType.GROUP, List.of(FILTER), false, mode))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(one, ScopeType.FULL_SEASON, List.of(FILTER), false, mode))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(one, ScopeType.OPEN_MATCH_DAYS, List.of(FILTER), false, mode))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Command(one, "2025-2026", ScopeType.FULL_SEASON, List.of(), false,
                RunTrigger.RETRY, "a", mode)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Command(one, "2025-2026", ScopeType.FULL_SEASON, List.of(), false,
                RunTrigger.MANUAL, " ", mode)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Command(one, "2025-2026", ScopeType.FULL_SEASON, List.of(), false,
                RunTrigger.MANUAL, "x".repeat(129), mode)).isInstanceOf(IllegalArgumentException.class);
    }
}
