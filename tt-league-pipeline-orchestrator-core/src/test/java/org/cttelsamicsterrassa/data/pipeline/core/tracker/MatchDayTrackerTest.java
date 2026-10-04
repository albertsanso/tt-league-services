package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedPlatformMatchGateway.match;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedPlatformMatchGateway;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformCompetitionCalendar.PlatformCalendarMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FCTT 2026-2027 shape: TERCERA, groups 1-2, phase "1a Fase", rounds 1-3, today 2026-10-04, two grace days. */
class MatchDayTrackerTest {

    private static final PipelineSource SOURCE = PipelineSource.FCTT;
    private static final String SEASON = "2026-2027";
    private static final String COMP = "TERCERA";
    private static final String PHASE = "1a Fase";
    private static final LocalDate D1 = LocalDate.parse("2026-09-27");
    private static final LocalDate D2 = LocalDate.parse("2026-10-04");
    private static final LocalDate D3 = LocalDate.parse("2026-10-11");

    private final ScriptedPlatformMatchGateway gateway = new ScriptedPlatformMatchGateway();
    private final InMemoryMatchDayRepository repository = new InMemoryMatchDayRepository();
    private final FakeRunClock clock = new FakeRunClock();
    private final MatchDayTracker tracker = new MatchDayTracker(gateway, repository, clock);
    private final MatchDayActions actions = new MatchDayActions(repository, clock);

    private final UUID m1 = UUID.randomUUID();
    private final UUID m2 = UUID.randomUUID();
    private final UUID m3 = UUID.randomUUID();
    private final UUID m4 = UUID.randomUUID();

    @BeforeEach
    void defaults() {
        gateway.today(LocalDate.parse("2026-10-04")).graceDays(2);
    }

    private static PlatformCalendarMatch m(UUID id, Integer group, int round, String state) {
        return match(id, COMP, group, PHASE, round, state);
    }

    private RecomputeOutcome recompute() {
        return tracker.recompute(SOURCE, SEASON, null);
    }

    private static MatchDayKey key(Integer group, int round) {
        return new MatchDayKey(SOURCE, SEASON, COMP, group, PHASE, round);
    }

    private MatchDay day(Integer group, int round) {
        return repository.dayOf(key(group, round)).orElseThrow();
    }

    private Map<UUID, MatchTracking> matches() {
        return repository.allMatches().stream().collect(Collectors.toMap(MatchTracking::matchId, Function.identity()));
    }

    private List<MatchDayEventKind> eventKinds(MatchDay day) {
        return repository.findEvents(day.id()).stream().map(MatchDayEvent::kind).toList();
    }

    @Test
    void createsMatchDaysOnlyForJornadasThePlatformReportsOpen() {
        gateway.jornada(COMP, 1, PHASE, 1, D1, D1, false, m(m1, 1, 1, "PLAYED"))
                .jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m2, 1, 2, "AWAITING_RESULT"))
                .jornada(COMP, 1, PHASE, 3, D3, D3, false, m(m3, 1, 3, "UPCOMING"));

        RecomputeOutcome outcome = recompute();

        assertThat(repository.allDays()).extracting(d -> d.key().round()).containsExactly(2);
        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.opened()).isEqualTo(1);
        assertThat(matches()).containsOnlyKeys(m2);
        assertThat(gateway.calendarReads).containsExactly(COMP);
    }

    @Test
    void severalMatchDaysCanBeOpenAtOnce() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "AWAITING_RESULT"))
                .jornada(COMP, 2, PHASE, 2, D2, D2, true, m(m2, 2, 2, "UPCOMING"))
                .jornada(COMP, 1, PHASE, 1, D1, D1, true, m(m3, 1, 1, "OVERDUE"));

        recompute();

        assertThat(repository.allDays()).hasSize(3).allMatch(d -> d.state() == MatchDayState.OPEN);
        assertThat(repository.findSourceSeasonsWithUnclosedDays()).containsExactly(new SourceSeason(SOURCE, SEASON));
    }

    @Test
    void windowComesFromThePlatformWithItsGracePeriod() {
        gateway.graceDays(3).jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "UPCOMING"));

        recompute();

        MatchDayWindow window = day(1, 2).window();
        assertThat(window.firstDate()).isEqualTo(D2);
        assertThat(window.graceDays()).isEqualTo(3);
        assertThat(window.end()).isEqualTo(D2.plusDays(3));
    }

    @Test
    void upcomingDayStaysUpcomingUntilItsWindowStarts() {
        gateway.jornada(COMP, 1, PHASE, 3, D3, D3, true, m(m1, 1, 3, "UPCOMING"));

        RecomputeOutcome outcome = recompute();

        assertThat(day(1, 3).state()).isEqualTo(MatchDayState.UPCOMING);
        assertThat(outcome.opened()).isZero();
    }

    @Test
    void undatedJornadaIsTrackedWithoutAWindow() {
        gateway.jornada(COMP, 1, PHASE, 3, null, null, true, m(m1, 1, 3, "UNDATED"));

        recompute();

        assertThat(day(1, 3).window().isDated()).isFalse();
        assertThat(day(1, 3).state()).isEqualTo(MatchDayState.UPCOMING);
        assertThat(matches().get(m1).status()).isEqualTo(TrackedMatchStatus.SCHEDULED);
    }

    @Test
    void mapsEveryPlatformStateToAMatchStatus() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true,
                m(m1, 1, 2, "PLAYED"), m(m2, 1, 2, "AWAITING_RESULT"), m(m3, 1, 2, "OVERDUE"),
                m(m4, 1, 2, "POSTPONED"), m(UUID.randomUUID(), 1, 2, "UPCOMING"));

        recompute();

        assertThat(repository.allMatches()).extracting(MatchTracking::status).containsExactlyInAnyOrder(
                TrackedMatchStatus.REPORTED, TrackedMatchStatus.AWAITING_RESULT, TrackedMatchStatus.OVERDUE,
                TrackedMatchStatus.POSTPONED, TrackedMatchStatus.SCHEDULED);
    }

    @Test
    void reportedAtRecordsTheFirstRunThatSawTheResult() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true,
                m(m1, 1, 2, "AWAITING_RESULT"), m(m2, 1, 2, "AWAITING_RESULT"));
        recompute();
        UUID run1 = UUID.randomUUID();
        Instant run1Finished = clock.now().plusSeconds(100);

        gateway.clear().jornada(COMP, 1, PHASE, 2, D2, D2, true,
                m(m1, 1, 2, "PLAYED"), m(m2, 1, 2, "AWAITING_RESULT"));
        tracker.recompute(SOURCE, SEASON, new RunRef(run1, run1Finished));
        assertThat(matches().get(m1).reportedAt()).isEqualTo(run1Finished);
        assertThat(matches().get(m1).reportedRunId()).isEqualTo(run1);

        UUID run2 = UUID.randomUUID();
        gateway.clear().jornada(COMP, 1, PHASE, 2, D2, D2, true,
                m(m1, 1, 2, "PLAYED"), m(m2, 1, 2, "PLAYED"));
        tracker.recompute(SOURCE, SEASON, new RunRef(run2, run1Finished.plusSeconds(500)));

        assertThat(matches().get(m1).reportedAt()).isEqualTo(run1Finished);
        assertThat(matches().get(m1).reportedRunId()).isEqualTo(run1);
        assertThat(matches().get(m2).reportedRunId()).isEqualTo(run2);
    }

    @Test
    void periodicRecomputeRecordsItsOwnTimeWithoutARunId() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "AWAITING_RESULT"), m(m2, 1, 2, "UPCOMING"));
        recompute();
        clock.advance(Duration.ofHours(1));
        gateway.clear().jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "PLAYED"), m(m2, 1, 2, "UPCOMING"));

        RecomputeOutcome outcome = recompute();

        assertThat(matches().get(m1).reportedAt()).isEqualTo(clock.now());
        assertThat(matches().get(m1).reportedRunId()).isNull();
        assertThat(outcome.reported()).isEqualTo(1);
        assertThat(repository.allEvents()).filteredOn(e -> e.kind() == MatchDayEventKind.MATCH_REPORTED)
                .singleElement().satisfies(e -> {
                    assertThat(e.matchId()).isEqualTo(m1);
                    assertThat(e.actor()).isEqualTo(MatchDayEvent.SYSTEM_ACTOR);
                });
    }

    @Test
    void runEventsCarryTheRunId() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "PLAYED"));
        UUID run = UUID.randomUUID();

        tracker.recompute(SOURCE, SEASON, new RunRef(run, clock.now()));

        assertThat(repository.allEvents()).isNotEmpty().allMatch(e -> run.equals(e.runId()));
    }

    @Test
    void closesWhenEveryMatchIsReportedOrPostponed() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "PLAYED"), m(m2, 1, 2, "POSTPONED"));

        RecomputeOutcome outcome = recompute();

        MatchDay day = day(1, 2);
        assertThat(day.state()).isEqualTo(MatchDayState.CLOSED);
        assertThat(day.closeReason()).isEqualTo(CloseReason.ALL_RESOLVED);
        assertThat(day.closedBy()).isEqualTo(MatchDayEvent.SYSTEM_ACTOR);
        assertThat(outcome.opened()).isEqualTo(1);
        assertThat(outcome.closed()).isEqualTo(1);
        assertThat(eventKinds(day)).contains(MatchDayEventKind.OPENED, MatchDayEventKind.CLOSED);
    }

    @Test
    void overdueAndAwaitingResultKeepTheDayOpen() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true,
                m(m1, 1, 2, "PLAYED"), m(m2, 1, 2, "OVERDUE"), m(m3, 1, 2, "AWAITING_RESULT"));

        recompute();

        assertThat(day(1, 2).state()).isEqualTo(MatchDayState.OPEN);
    }

    @Test
    void postponedMatchStaysTrackedAfterTheDayClosesAndReopensItWhenItReturns() {
        gateway.jornada(COMP, 1, PHASE, 1, D1, D1, true, m(m1, 1, 1, "PLAYED"), m(m2, 1, 1, "POSTPONED"));
        recompute();
        assertThat(day(1, 1).state()).isEqualTo(MatchDayState.CLOSED);
        assertThat(matches()).containsKeys(m1, m2);

        gateway.clear().jornada(COMP, 1, PHASE, 1, D1, D1, true, m(m1, 1, 1, "PLAYED"), m(m2, 1, 1, "AWAITING_RESULT"));
        RecomputeOutcome outcome = recompute();

        assertThat(matches().get(m2).status()).isEqualTo(TrackedMatchStatus.AWAITING_RESULT);
        assertThat(day(1, 1).state()).isEqualTo(MatchDayState.OPEN);
        assertThat(outcome.reopened()).isEqualTo(1);
        assertThat(eventKinds(day(1, 1))).contains(MatchDayEventKind.REOPENED);
    }

    @Test
    void autoClosedDayReopensWhenAMatchBecomesUnresolved() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "PLAYED"));
        recompute();
        assertThat(day(1, 2).state()).isEqualTo(MatchDayState.CLOSED);

        gateway.clear().jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "PLAYED"), m(m2, 1, 2, "OVERDUE"));
        recompute();

        MatchDay day = day(1, 2);
        assertThat(day.state()).isEqualTo(MatchDayState.OPEN);
        assertThat(day.closeReason()).isNull();
    }

    @Test
    void manuallyClosedDayIsNotReopenedByTheTracker() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"));
        recompute();
        actions.close(day(1, 2).id(), "ana", "league cancelled it");

        recompute();
        recompute();

        assertThat(day(1, 2).state()).isEqualTo(MatchDayState.CLOSED);
        assertThat(day(1, 2).closeReason()).isEqualTo(CloseReason.MANUAL);
    }

    @Test
    void matchesAreRefreshedOnAManuallyClosedDayWhileThePlatformReportsItOpen() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"));
        recompute();
        actions.close(day(1, 2).id(), "ana", null);

        gateway.clear().jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "PLAYED"));
        recompute();

        assertThat(matches().get(m1).status()).isEqualTo(TrackedMatchStatus.REPORTED);
        assertThat(day(1, 2).state()).isEqualTo(MatchDayState.CLOSED);
    }

    @Test
    void removesMatchesThePlatformNoLongerListsInAJornada() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"), m(m2, 1, 2, "OVERDUE"));
        recompute();

        gateway.clear().jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"));
        recompute();

        assertThat(matches()).containsOnlyKeys(m1);
        assertThat(repository.allEvents()).filteredOn(e -> e.kind() == MatchDayEventKind.MATCH_REMOVED)
                .singleElement().satisfies(e -> assertThat(e.matchId()).isEqualTo(m2));
    }

    @Test
    void closesAJornadaThePlatformNoLongerReportsAndRemovesItsMatches() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"));
        gateway.jornada(COMP, 2, PHASE, 2, D2, D2, true, m(m2, 2, 2, "OVERDUE"));
        recompute();

        gateway.clear().jornada(COMP, 2, PHASE, 2, D2, D2, true, m(m2, 2, 2, "OVERDUE"));
        RecomputeOutcome outcome = recompute();

        MatchDay removed = day(1, 2);
        assertThat(removed.state()).isEqualTo(MatchDayState.CLOSED);
        assertThat(removed.closeReason()).isEqualTo(CloseReason.REMOVED);
        assertThat(day(2, 2).state()).isEqualTo(MatchDayState.OPEN);
        assertThat(matches()).containsOnlyKeys(m2);
        assertThat(outcome.closed()).isEqualTo(1);
        assertThat(eventKinds(removed)).contains(MatchDayEventKind.MATCH_REMOVED, MatchDayEventKind.CLOSED);
    }

    @Test
    void movesAMatchWhoseRoundChanged() {
        gateway.jornada(COMP, 1, PHASE, 1, D1, D1, true, m(m1, 1, 1, "OVERDUE"), m(m2, 1, 1, "OVERDUE"));
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m3, 1, 2, "UPCOMING"));
        recompute();
        UUID round2 = day(1, 2).id();

        gateway.clear().jornada(COMP, 1, PHASE, 1, D1, D1, true, m(m1, 1, 1, "OVERDUE"));
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m3, 1, 2, "UPCOMING"), m(m2, 1, 2, "UPCOMING"));
        recompute();

        assertThat(matches().get(m2).matchDayId()).isEqualTo(round2);
        assertThat(matches()).hasSize(3);
        assertThat(eventKinds(day(1, 1))).contains(MatchDayEventKind.MATCH_REMOVED);
    }

    @Test
    void skipsOpenJornadasWithoutACompetitionAndCountsThem() {
        gateway.jornada(null, null, null, 1, D2, D2, true);
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"));

        RecomputeOutcome outcome = recompute();

        assertThat(outcome.skippedGroups()).isEqualTo(1);
        assertThat(repository.allDays()).hasSize(1);
        assertThat(gateway.calendarReads).containsExactly(COMP);
    }

    @Test
    void readsEachCompetitionCalendarOnce() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"));
        gateway.jornada(COMP, 2, PHASE, 2, D2, D2, true, m(m2, 2, 2, "OVERDUE"));
        gateway.jornada("SEGONA", 1, PHASE, 2, D2, D2, true, match(m3, "SEGONA", 1, PHASE, 2, "OVERDUE"));

        recompute();

        assertThat(gateway.calendarReads).containsExactly(COMP, "SEGONA");
    }

    @Test
    void doesNothingWhenNothingIsOpenOrTracked() {
        gateway.jornada(COMP, 1, PHASE, 1, D1, D1, false, m(m1, 1, 1, "PLAYED"));

        RecomputeOutcome outcome = recompute();

        assertThat(outcome).isEqualTo(new RecomputeOutcome(SOURCE, SEASON, 0, 0, 0, 0, 0, 0, 0));
        assertThat(repository.applyCalls()).isZero();
        assertThat(gateway.calendarReads).isEmpty();
    }

    @Test
    void inconsistentCountsWriteNothing() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"));
        recompute();
        int applied = repository.applyCalls();
        List<MatchDay> before = repository.allDays();
        gateway.calendars.get(COMP).add(m(m2, 1, 2, "UPCOMING"));

        assertThatThrownBy(this::recompute).isInstanceOf(TrackerInconsistencyException.class);

        assertThat(repository.applyCalls()).isEqualTo(applied);
        assertThat(repository.allDays()).isEqualTo(before);
        assertThat(matches()).containsOnlyKeys(m1);
    }

    @Test
    void differentTodayInTheCalendarWritesNothing() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"));
        gateway.calendarToday(LocalDate.parse("2026-10-05"));

        assertThatThrownBy(this::recompute).isInstanceOf(TrackerInconsistencyException.class);

        assertThat(repository.applyCalls()).isZero();
    }

    @Test
    void platformFailureWritesNothing() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"));
        gateway.failWith(GatewayException.Kind.UNAVAILABLE, 503);

        assertThatThrownBy(this::recompute).isInstanceOf(GatewayException.class);

        assertThat(repository.applyCalls()).isZero();
    }

    @Test
    void unknownCalendarStateWritesNothing() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "CANCELLED"));

        assertThatThrownBy(this::recompute).isInstanceOf(IllegalArgumentException.class);

        assertThat(repository.applyCalls()).isZero();
    }

    @Test
    void repeatedRecomputeKeepsTheStateAndBumpsVersions() {
        gateway.jornada(COMP, 1, PHASE, 2, D2, D2, true, m(m1, 1, 2, "OVERDUE"));
        recompute();
        recompute();

        assertThat(repository.allDays()).singleElement().satisfies(d -> assertThat(d.version()).isEqualTo(1));
        assertThat(eventKinds(day(1, 2))).containsExactly(MatchDayEventKind.OPENED);
    }
}
