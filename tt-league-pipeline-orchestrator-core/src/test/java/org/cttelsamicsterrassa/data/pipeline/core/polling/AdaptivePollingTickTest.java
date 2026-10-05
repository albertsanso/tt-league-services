package org.cttelsamicsterrassa.data.pipeline.core.polling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.polling.PollingFixtures.SEASON;
import static org.cttelsamicsterrassa.data.pipeline.core.polling.PollingFixtures.ZONE;
import static org.cttelsamicsterrassa.data.pipeline.core.polling.PollingFixtures.at;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPendingTriggerRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPollPolicyRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPollScheduleRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingPendingTriggerEvents;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingPollingAlerts;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestStatusGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.StubOpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.polling.AdaptivePollingTick.Action;
import org.cttelsamicsterrassa.data.pipeline.core.polling.AdaptivePollingTick.TickResult;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollScheduleException;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.BcnesaCompetitionNames;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestMatchDayStatus;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestStatusRow;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuilder;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.CloseReason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.junit.jupiter.api.Test;

class AdaptivePollingTickTest {

    private static final PipelineSource SOURCE = PipelineSource.FCTT;

    private final FakeRunClock clock = new FakeRunClock(at("2026-10-04T12:00"));
    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final InMemoryMatchDayRepository matchDays = new InMemoryMatchDayRepository();
    private final FlakySchedules schedules = new FlakySchedules();
    private final InMemoryPollPolicyRepository policies = new InMemoryPollPolicyRepository();
    private final ScriptedIngestStatusGateway status = new ScriptedIngestStatusGateway();
    private final RecordingPollingAlerts alerts = new RecordingPollingAlerts();
    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private final RunLauncher launcher = new RunLauncher(runs, dispatcher, clock, new RecordingObserver());
    private final TriggerRun triggerRun = new TriggerRun(runs, new InMemoryPendingTriggerRepository(),
            new StubOpenMatchDayScopeResolver(), launcher, new RecordingPendingTriggerEvents(), clock);
    private final AdaptivePollingTick tick = new AdaptivePollingTick(matchDays, status, schedules,
            new PollingSettingsProvider(policies, PollingSettings.defaults()), triggerRun, runs, alerts,
            new ScopeBuilder(BcnesaCompetitionNames.defaults()), clock, SEASON, ZONE);

    // --- fixtures ---

    private MatchDay openDay(String competition, int round, TrackedMatchStatus status, String matchAt) {
        MatchDayKey key = new MatchDayKey(SOURCE, SEASON, competition, 1, "1a Fase", round);
        MatchDay day = MatchDay.create(UUID.randomUUID(), key,
                new MatchDayWindow(at(matchAt).atZone(ZONE).toLocalDate(), at(matchAt).atZone(ZONE).toLocalDate(), 2),
                clock.now()).open(clock.now());
        MatchTracking match = MatchTracking.first(UUID.randomUUID(), day.id(), status, at(matchAt), "H", "A",
                clock.now(), null, null);
        matchDays.apply(new MatchDayChangeSet(List.of(day), List.of(match), Set.of(), List.of()));
        return matchDays.findById(day.id()).orElseThrow();
    }

    private void scriptStatus(IngestStatusRow... rows) {
        status.returning(new IngestMatchDayStatus(SOURCE, SEASON, List.of(rows)));
    }

    private static IngestStatusRow row(String category, String gender, int round) {
        return new IngestStatusRow(SEASON, category, "G1", "1a Fase", gender, null, round, "scheduled", null, null);
    }

    private PipelineRun run(UUID id) {
        return runs.findById(id).orElseThrow();
    }

    /** Finishes the run with a terminal status that needs no import details beyond a job id. */
    private void finish(UUID runId, RunStatus terminal) {
        PipelineRun run = run(runId);
        Instant at = clock.now();
        PipelineRun started = run.startIngest("ingest-1", at);
        PipelineRun done = switch (terminal) {
            case NO_CHANGES -> started.noChanges(at);
            case SUCCEEDED -> started.packed(at).startImport(UUID.randomUUID(), at).succeed(at);
            case PARTIAL -> started.packed(at).startImport(UUID.randomUUID(), at).partial(at);
            default -> throw new IllegalArgumentException(terminal.toString());
        };
        runs.update(done);
    }

    private PollSchedule full() {
        return schedules.findBySourceAndSeason(SOURCE, SEASON).stream().filter(PollSchedule::isFullRefresh)
                .findFirst().orElseThrow();
    }

    private List<PollSchedule> groups() {
        return schedules.findBySourceAndSeason(SOURCE, SEASON).stream().filter(s -> !s.isFullRefresh()).toList();
    }

    /** Runs the season-start full refresh to completion so group polling can start. */
    private void completeSeasonStart() {
        TickResult first = tick.tick(SOURCE);
        assertThat(first.action()).isEqualTo(Action.FULL_REFRESH_LAUNCHED);
        finish(first.runId(), RunStatus.SUCCEEDED);
    }

    // --- tests ---

    @Test
    void theFirstTickOfASeasonLaunchesAFullSeasonRunAndStoresTheRefreshRow() {
        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.FULL_REFRESH_LAUNCHED);
        PipelineRun run = run(result.runId());
        assertThat(run.scope().isFullSeason()).isTrue();
        assertThat(run.trigger()).isEqualTo(RunTrigger.SCHEDULED);
        assertThat(run.requestedBy()).isEqualTo(AdaptivePollingTick.REQUESTED_BY);
        assertThat(run.force()).isFalse();
        PollSchedule full = full();
        assertThat(full.level()).isEqualTo(PolicyLevel.FULL_REFRESH);
        assertThat(full.pendingRunId()).isEqualTo(result.runId());
        assertThat(full.interval()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void anActiveRunLeavesEveryRowUntouchedAndLaunchesNothing() {
        runs.create(PipelineRun.queue(UUID.randomUUID(), SOURCE, SEASON, RunScope.fullSeason(), false,
                RunTrigger.MANUAL, "alice", null, clock.now()));

        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.SKIPPED);
        PollSchedule full = full();
        assertThat(full.pendingRunId()).isNull();
        assertThat(full.lastRunAt()).isNull();
        assertThat(dispatcher.dispatched).isEmpty();
        // the next tick, with the manual run still active, behaves the same
        assertThat(tick.tick(SOURCE).action()).isEqualTo(Action.SKIPPED);
    }

    @Test
    void theFinishedFullRefreshOutcomeIsApplied() {
        completeSeasonStart();
        clock.advance(Duration.ofMinutes(1));

        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.NOTHING_DUE);
        PollSchedule full = full();
        assertThat(full.pendingRunId()).isNull();
        assertThat(full.lastRunAt()).isNotNull();
        assertThat(full.nextRunAt()).isEqualTo(full.lastRunAt().plus(Duration.ofDays(7)));
    }

    @Test
    void dueUnitsAreCombinedIntoOneGroupRun() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.AWAITING_RESULT, "2026-10-04T10:00");
        openDay("tercera-femenino", 2, TrackedMatchStatus.SCHEDULED, "2026-10-20T10:00");
        scriptStatus(row("tercera", "male", 5), row("tercera", "female", 2));

        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.GROUP_LAUNCHED);
        PipelineRun run = run(result.runId());
        assertThat(run.scope().filters()).containsExactlyInAnyOrder(
                new ScopeFilter("tercera", "G1", "1a Fase", null, "male", List.of(5)),
                new ScopeFilter("tercera", "G1", "1a Fase", null, "female", List.of(2)));
        assertThat(run.trigger()).isEqualTo(RunTrigger.SCHEDULED);
        assertThat(run.requestedBy()).isEqualTo("system:polling");
        assertThat(groups()).hasSize(2).allSatisfy(unit -> assertThat(unit.pendingRunId()).isEqualTo(result.runId()));
        assertThat(groups()).extracting(PollSchedule::level)
                .containsExactlyInAnyOrder(PolicyLevel.MATCH_DAY, PolicyLevel.OPEN);
    }

    @Test
    void nothingIsLaunchedWhileNothingIsDue() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.SCHEDULED, "2026-10-20T10:00");
        scriptStatus(row("tercera", "male", 5));
        TickResult group = tick.tick(SOURCE);
        finish(group.runId(), RunStatus.SUCCEEDED);
        clock.advance(Duration.ofMinutes(5));

        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.NOTHING_DUE);
        assertThat(runs.findActiveBySource(SOURCE)).isEmpty();
    }

    @Test
    void aUnitBecomesDueAgainAfterItsInterval() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.SCHEDULED, "2026-10-20T10:00");
        scriptStatus(row("tercera", "male", 5));
        TickResult group = tick.tick(SOURCE);
        finish(group.runId(), RunStatus.SUCCEEDED);
        clock.advance(Duration.ofHours(25));

        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.GROUP_LAUNCHED);
    }

    @Test
    void noChangesOutcomesBuildUpTheBackOffCounter() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.SCHEDULED, "2026-10-20T10:00");
        scriptStatus(row("tercera", "male", 5));
        for (int i = 0; i < 3; i++) {
            TickResult group = tick.tick(SOURCE);
            assertThat(group.action()).isEqualTo(Action.GROUP_LAUNCHED);
            finish(group.runId(), RunStatus.NO_CHANGES);
            clock.advance(Duration.ofHours(25));
        }

        tick.tick(SOURCE);

        PollSchedule unit = groups().get(0);
        assertThat(unit.consecutiveNoChange()).isEqualTo(3);
        assertThat(unit.interval()).isEqualTo(Duration.ofHours(48));
    }

    @Test
    void aSuccessfulRunResetsTheCounter() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.SCHEDULED, "2026-10-20T10:00");
        scriptStatus(row("tercera", "male", 5));
        TickResult first = tick.tick(SOURCE);
        finish(first.runId(), RunStatus.NO_CHANGES);
        clock.advance(Duration.ofHours(25));
        TickResult second = tick.tick(SOURCE);
        finish(second.runId(), RunStatus.SUCCEEDED);

        tick.tick(SOURCE);

        assertThat(groups().get(0).consecutiveNoChange()).isZero();
    }

    @Test
    void theFullRefreshRunsAgainAfterAWeek() {
        completeSeasonStart();
        clock.advance(Duration.ofDays(7).plusMinutes(1));

        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.FULL_REFRESH_LAUNCHED);
        assertThat(run(result.runId()).scope().isFullSeason()).isTrue();
    }

    @Test
    void aGroupRunIsRejectedWhileAnotherRunIsActiveAndRowsStayUnlaunched() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.AWAITING_RESULT, "2026-10-04T10:00");
        scriptStatus(row("tercera", "male", 5));
        runs.create(PipelineRun.queue(UUID.randomUUID(), SOURCE, SEASON, RunScope.fullSeason(), false,
                RunTrigger.MANUAL, "alice", null, clock.now()));

        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.SKIPPED);
        assertThat(groups()).hasSize(1).allSatisfy(unit -> assertThat(unit.pendingRunId()).isNull());
    }

    @Test
    void anOverdueUnitIsPolledOnceThenStoppedWithASingleAlert() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.OVERDUE, "2026-08-20T10:00");
        scriptStatus(row("tercera", "male", 5));
        TickResult poll = tick.tick(SOURCE);
        assertThat(poll.action()).isEqualTo(Action.GROUP_LAUNCHED);
        assertThat(groups().get(0).level()).isEqualTo(PolicyLevel.OVERDUE);
        finish(poll.runId(), RunStatus.NO_CHANGES);
        clock.advance(Duration.ofHours(25));

        TickResult stopped = tick.tick(SOURCE);
        TickResult again = tick.tick(SOURCE);

        assertThat(stopped.action()).isEqualTo(Action.NOTHING_DUE);
        assertThat(again.action()).isEqualTo(Action.NOTHING_DUE);
        PollSchedule unit = groups().get(0);
        assertThat(unit.level()).isEqualTo(PolicyLevel.STOPPED);
        assertThat(unit.stopReason()).isEqualTo(PollDecision.OVERDUE_LIMIT);
        assertThat(unit.stoppedAt()).isNotNull();
        assertThat(unit.nextRunAt()).isNull();
        assertThat(alerts.stopped).hasSize(1);
        assertThat(alerts.stopped.get(0).id()).isEqualTo(unit.id());
        assertThat(runs.findActiveBySource(SOURCE)).isEmpty();
    }

    @Test
    void aResumedUnitIsDueNowAndStopsAgainAfterOnePoll() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.OVERDUE, "2026-08-20T10:00");
        scriptStatus(row("tercera", "male", 5));
        TickResult poll = tick.tick(SOURCE);
        finish(poll.runId(), RunStatus.NO_CHANGES);
        clock.advance(Duration.ofHours(25));
        tick.tick(SOURCE);
        PollSchedule stopped = groups().get(0);
        schedules.save(stopped.resumed(clock.now()));

        TickResult resumed = tick.tick(SOURCE);

        assertThat(resumed.action()).isEqualTo(Action.GROUP_LAUNCHED);
        finish(resumed.runId(), RunStatus.NO_CHANGES);
        clock.advance(Duration.ofHours(25));
        tick.tick(SOURCE);
        assertThat(groups().get(0).level()).isEqualTo(PolicyLevel.STOPPED);
        assertThat(alerts.stopped).hasSize(2);
    }

    @Test
    void anUnmatchedOpenDayAlertsOncePerDayAndLaunchesNothing() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.AWAITING_RESULT, "2026-10-04T10:00");
        scriptStatus(row("tercera", "male", 9));

        TickResult first = tick.tick(SOURCE);
        TickResult second = tick.tick(SOURCE);

        assertThat(first.action()).isEqualTo(Action.SCOPE_UNMATCHED);
        assertThat(second.action()).isEqualTo(Action.SCOPE_UNMATCHED);
        assertThat(alerts.unmatched).hasSize(1);
        assertThat(alerts.unmatched.get(0)).contains("FCTT", "round 5");
        assertThat(runs.findActiveBySource(SOURCE)).isEmpty();
        assertThat(groups()).isEmpty();

        clock.advance(Duration.ofDays(1));
        tick.tick(SOURCE);
        assertThat(alerts.unmatched).hasSize(2);
    }

    @Test
    void aMissingStatusFileWaitsForTheFullRefresh() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.AWAITING_RESULT, "2026-10-04T10:00");

        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.NO_STATUS);
        assertThat(runs.findActiveBySource(SOURCE)).isEmpty();
        assertThat(alerts.unmatched).isEmpty();
    }

    @Test
    void unitsWhoseMatchDaysAreNoLongerOpenAreDeleted() {
        completeSeasonStart();
        MatchDay day = openDay("tercera-masculino", 5, TrackedMatchStatus.SCHEDULED, "2026-10-20T10:00");
        scriptStatus(row("tercera", "male", 5));
        TickResult group = tick.tick(SOURCE);
        finish(group.runId(), RunStatus.SUCCEEDED);
        assertThat(groups()).hasSize(1);
        matchDays.apply(new MatchDayChangeSet(List.of(day.close(CloseReason.MANUAL, "ops", clock.now())), List.of(),
                Set.of(), List.of()));

        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.NOTHING_DUE);
        assertThat(groups()).isEmpty();
        assertThat(full()).isNotNull();
    }

    @Test
    void aMissingPendingRunIsClearedWithoutAnOutcome() {
        completeSeasonStart();
        clock.advance(Duration.ofMinutes(1));
        tick.tick(SOURCE);
        PollSchedule full = full();
        Instant lastRun = full.lastRunAt();
        schedules.save(PollSchedule.restore(full.id(), full.source(), full.season(), full.scopeKey(), null,
                full.level(), full.interval(), full.consecutiveNoChange(), full.nextRunAt(), full.lastRunAt(),
                UUID.randomUUID(), null, null, null, full.version()));

        tick.tick(SOURCE);

        assertThat(full().pendingRunId()).isNull();
        assertThat(full().lastRunAt()).isEqualTo(lastRun);
    }

    @Test
    void aStaleScheduleAbortsTheTickWithoutALaunch() {
        completeSeasonStart();
        openDay("tercera-masculino", 5, TrackedMatchStatus.AWAITING_RESULT, "2026-10-04T10:00");
        scriptStatus(row("tercera", "male", 5));
        schedules.staleOnSave = true;

        TickResult result = tick.tick(SOURCE);

        assertThat(result.action()).isEqualTo(Action.STALE);
        assertThat(runs.findActiveBySource(SOURCE)).isEmpty();
        schedules.staleOnSave = false;
        assertThat(tick.tick(SOURCE).action()).isEqualTo(Action.GROUP_LAUNCHED);
    }

    /** Fails every save with a stale-write error while {@code staleOnSave} is set. */
    private static final class FlakySchedules extends InMemoryPollScheduleRepository {

        private boolean staleOnSave;

        @Override
        public synchronized PollSchedule save(PollSchedule schedule) {
            if (staleOnSave) {
                throw new StalePollScheduleException(schedule.id(), "changed concurrently");
            }
            return super.save(schedule);
        }
    }
}
