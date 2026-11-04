package org.cttelsamicsterrassa.data.pipeline.core.polling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPendingTriggerRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingPendingTriggerEvents;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestStatusGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.StubOpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.BcnesaCompetitionNames;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestMatchDayStatus;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestStatusRow;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.OpenMatchDays;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuilder;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayActions;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayEvent;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayEventKind;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayNotFoundException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ConflictMode;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;
import org.junit.jupiter.api.Test;

class MatchDayRefreshTest {

    private static final String SEASON = "2026-2027";
    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private final InMemoryMatchDayRepository matchDays = new InMemoryMatchDayRepository();
    private final ScriptedIngestStatusGateway status = new ScriptedIngestStatusGateway();
    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final FakeRunClock clock = new FakeRunClock();
    private final ScopeBuilder builder = new ScopeBuilder(BcnesaCompetitionNames.defaults());
    private final TriggerRun triggerRun = new TriggerRun(runs, new InMemoryPendingTriggerRepository(),
            new StubOpenMatchDayScopeResolver(),
            new RunLauncher(runs, new RecordingDispatcher(), clock, new RecordingObserver()),
            new RecordingPendingTriggerEvents(), clock);
    private final MatchDayRefresh refresh = new MatchDayRefresh(matchDays, status, builder, triggerRun,
            new MatchDayActions(matchDays, clock));

    private MatchDay store(PipelineSource source, String competition, Integer group, String phase, int round) {
        MatchDayKey key = new MatchDayKey(source, SEASON, competition, group, phase, round);
        MatchDay day = MatchDay.create(UUID.randomUUID(), key,
                new MatchDayWindow(LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-04"), 2), NOW).open(NOW);
        MatchTracking open = MatchTracking.first(UUID.randomUUID(), day.id(), TrackedMatchStatus.AWAITING_RESULT,
                NOW, "H", "A", NOW, null, null);
        MatchTracking done = MatchTracking.first(UUID.randomUUID(), day.id(), TrackedMatchStatus.REPORTED, NOW, "H2",
                "A2", NOW, NOW, null);
        matchDays.apply(new MatchDayChangeSet(List.of(day), List.of(open, done), Set.of(), List.of()));
        return day;
    }

    private void fcttStatus(int round) {
        status.returning(new IngestMatchDayStatus(PipelineSource.FCTT, SEASON, List.of(new IngestStatusRow(SEASON,
                "tercera", "G1", "1a Fase", "male", "Barcelona", round, "scheduled", null, null))));
    }

    private PipelineRun activeRun() {
        return runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, SEASON, RunScope.fullSeason(),
                false, RunTrigger.SCHEDULED, "system:scheduler", null, clock.now()));
    }

    private List<MatchDayEvent> refreshEvents(MatchDay day) {
        return matchDays.findEvents(day.id()).stream()
                .filter(event -> event.kind() == MatchDayEventKind.REFRESH_REQUESTED)
                .toList();
    }

    @Test
    void fcttRefreshUsesTheSameFiltersAsOpenMatchDays() {
        MatchDay day = store(PipelineSource.FCTT, "tercera-masculino", 1, "1a Fase", 4);
        fcttStatus(4);
        RunScope expected = new TrackerOpenMatchDayScopeResolver(matchDays, status, builder)
                .resolve(PipelineSource.FCTT, SEASON);

        List<Outcome> outcomes = refresh.refresh(day.id(), true, "ana", ConflictMode.REJECT);

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Created.class, created -> {
            PipelineRun run = created.run();
            assertThat(run.scope().filters()).containsExactlyElementsOf(expected.filters());
            assertThat(run.scope().filters()).containsExactly(
                    new ScopeFilter("tercera", "G1", "1a Fase", "Barcelona", "male", List.of(4)));
            assertThat(run.force()).isTrue();
            assertThat(run.trigger()).isEqualTo(RunTrigger.MANUAL);
            assertThat(run.requestedBy()).isEqualTo("ana");
        });
        assertThat(refreshEvents(day)).singleElement().satisfies(event -> {
            assertThat(event.actor()).isEqualTo("ana");
            assertThat(event.runId()).isEqualTo(((Outcome.Created) outcomes.get(0)).run().id());
        });
    }

    @Test
    void bcnesaRefreshUsesTheSameFiltersAsOpenMatchDays() {
        MatchDay day = store(PipelineSource.BCNESA, "Primera", 1, "Fase 2", 2);
        status.returning(new IngestMatchDayStatus(PipelineSource.BCNESA, SEASON, List.of(new IngestStatusRow(SEASON,
                "rtb-primera", "G1", "Fase 2", null, null, 2, "scheduled", null, null))));
        RunScope expected = new TrackerOpenMatchDayScopeResolver(matchDays, status, builder)
                .resolve(PipelineSource.BCNESA, SEASON);

        List<Outcome> outcomes = refresh.refresh(day.id(), false, "ana", ConflictMode.REJECT);

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Created.class,
                created -> assertThat(created.run().scope().filters()).containsExactlyElementsOf(expected.filters()));
    }

    @Test
    void rfetmRefreshCoversTheWholeCategory() {
        MatchDay day = store(PipelineSource.RFETM, "super-divisio-masculino", 1, null, 4);
        status.returning(new IngestMatchDayStatus(PipelineSource.RFETM, SEASON, List.of(new IngestStatusRow(SEASON,
                "super-divisio", "1", null, "masculino", null, 4, "scheduled", null, null))));

        List<Outcome> outcomes = refresh.refresh(day.id(), false, "ana", ConflictMode.REJECT);

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Created.class,
                created -> assertThat(created.run().scope().filters())
                        .containsExactly(new ScopeFilter("super-divisio", null, null, null, null, List.of(4))));
    }

    @Test
    void aClosedDayCanBeRefreshed() {
        MatchDay day = store(PipelineSource.FCTT, "tercera-masculino", 1, "1a Fase", 4);
        fcttStatus(4);
        new MatchDayActions(matchDays, clock).close(day.id(), "ana", null);
        assertThat(OpenMatchDays.load(matchDays, PipelineSource.FCTT, SEASON)).isEmpty();

        List<Outcome> outcomes = refresh.refresh(day.id(), false, "bea", ConflictMode.REJECT);

        assertThat(outcomes).singleElement().isInstanceOf(Outcome.Created.class);
    }

    @Test
    void aQueuedRefreshRecordsTheActiveRun() {
        MatchDay day = store(PipelineSource.FCTT, "tercera-masculino", 1, "1a Fase", 4);
        fcttStatus(4);
        PipelineRun active = activeRun();

        List<Outcome> outcomes = refresh.refresh(day.id(), false, "ana", ConflictMode.QUEUE);

        assertThat(outcomes).singleElement().isInstanceOf(Outcome.Queued.class);
        assertThat(refreshEvents(day)).singleElement().satisfies(event -> {
            assertThat(event.runId()).isNull();
            assertThat(event.note()).contains(active.id().toString());
        });
    }

    @Test
    void aRejectedRefreshRecordsNothing() {
        MatchDay day = store(PipelineSource.FCTT, "tercera-masculino", 1, "1a Fase", 4);
        fcttStatus(4);
        activeRun();

        List<Outcome> outcomes = refresh.refresh(day.id(), false, "ana", ConflictMode.REJECT);

        assertThat(outcomes).singleElement().isInstanceOf(Outcome.Rejected.class);
        assertThat(refreshEvents(day)).isEmpty();
    }

    @Test
    void withoutAnIngestStatusItIsUnavailableAndRecordsNothing() {
        MatchDay day = store(PipelineSource.FCTT, "tercera-masculino", 1, "1a Fase", 4);

        List<Outcome> outcomes = refresh.refresh(day.id(), false, "ana", ConflictMode.REJECT);

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Unavailable.class, unavailable -> {
            assertThat(unavailable.code()).isEqualTo(TrackerOpenMatchDayScopeResolver.NO_INGEST_STATUS);
            assertThat(unavailable.source()).isEqualTo(PipelineSource.FCTT);
        });
        assertThat(refreshEvents(day)).isEmpty();
        assertThat(runs.findActiveBySource(PipelineSource.FCTT)).isEmpty();
    }

    @Test
    void aRoundWithoutAStatusRowRefreshesItsGroupLimitedToThatRound() {
        MatchDay day = store(PipelineSource.FCTT, "tercera-masculino", 1, "1a Fase", 4);
        fcttStatus(9);

        List<Outcome> outcomes = refresh.refresh(day.id(), false, "ana", ConflictMode.REJECT);

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Created.class,
                created -> assertThat(created.run().scope().filters()).containsExactly(
                        new ScopeFilter("tercera", "G1", "1a Fase", "Barcelona", "male", List.of(4))));
        assertThat(refreshEvents(day)).hasSize(1);
    }

    @Test
    void aGroupWithoutAnyStatusRowIsUnavailableAndRecordsNothing() {
        MatchDay day = store(PipelineSource.FCTT, "tercera-masculino", 2, "1a Fase", 4);
        fcttStatus(4);

        List<Outcome> outcomes = refresh.refresh(day.id(), false, "ana", ConflictMode.REJECT);

        assertThat(outcomes).singleElement().isInstanceOfSatisfying(Outcome.Unavailable.class,
                unavailable -> assertThat(unavailable.code()).isEqualTo("SCOPE_UNMATCHED"));
        assertThat(refreshEvents(day)).isEmpty();
    }

    @Test
    void anUnknownMatchDayIsNotFound() {
        assertThatThrownBy(() -> refresh.refresh(UUID.randomUUID(), false, "ana", ConflictMode.REJECT))
                .isInstanceOf(MatchDayNotFoundException.class);
    }
}
