package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.StaleMatchDayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchDayActionsTest {

    private final InMemoryMatchDayRepository repository = new InMemoryMatchDayRepository();
    private final FakeRunClock clock = new FakeRunClock();
    private final MatchDayActions actions = new MatchDayActions(repository, clock);

    private MatchDay day;
    private MatchTracking reported;
    private MatchTracking overdue;

    @BeforeEach
    void seed() {
        day = TrackerFixtures.openDay();
        reported = TrackerFixtures.match(day.id(), TrackedMatchStatus.REPORTED);
        overdue = TrackerFixtures.match(day.id(), TrackedMatchStatus.OVERDUE);
        repository.apply(new MatchDayChangeSet(List.of(day), List.of(reported, overdue), java.util.Set.of(), List.of()));
        clock.advance(Duration.ofMinutes(5));
    }

    private MatchDay stored() {
        return repository.findById(day.id()).orElseThrow();
    }

    private List<MatchDayEvent> events() {
        return repository.findEvents(day.id());
    }

    @Test
    void closeRecordsActorTimeAndNote() {
        Instant at = clock.now();

        actions.close(day.id(), "ana", "league cancelled it");

        assertThat(stored().state()).isEqualTo(MatchDayState.CLOSED);
        assertThat(stored().closeReason()).isEqualTo(CloseReason.MANUAL);
        assertThat(stored().closedBy()).isEqualTo("ana");
        assertThat(stored().closedAt()).isEqualTo(at);
        assertThat(events()).singleElement().satisfies(event -> {
            assertThat(event.kind()).isEqualTo(MatchDayEventKind.CLOSED);
            assertThat(event.actor()).isEqualTo("ana");
            assertThat(event.occurredAt()).isEqualTo(at);
            assertThat(event.note()).isEqualTo("league cancelled it");
        });
    }

    @Test
    void closingAClosedDayIsIllegal() {
        actions.close(day.id(), "ana", null);

        assertThatThrownBy(() -> actions.close(day.id(), "ana", null))
                .isInstanceOf(IllegalMatchDayTransitionException.class);
    }

    @Test
    void reopenRecordsActorAndOnlyWorksOnClosedDays() {
        assertThatThrownBy(() -> actions.reopen(day.id(), "ana", null))
                .isInstanceOf(IllegalMatchDayTransitionException.class);
        actions.close(day.id(), "ana", null);

        actions.reopen(day.id(), "bea", "mistake");

        assertThat(stored().state()).isEqualTo(MatchDayState.OPEN);
        assertThat(events()).extracting(MatchDayEvent::kind, MatchDayEvent::actor)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(MatchDayEventKind.CLOSED, "ana"),
                        org.assertj.core.api.Assertions.tuple(MatchDayEventKind.REOPENED, "bea"));
    }

    @Test
    void ignoreMarksTheMatchAndRecordsWhoAndWhen() {
        Instant at = clock.now();

        actions.ignoreMatch(day.id(), overdue.matchId(), "ana", "forfeit pending");

        MatchTracking stored = repository.findMatch(overdue.matchId()).orElseThrow();
        assertThat(stored.isIgnored()).isTrue();
        assertThat(stored.ignoredBy()).isEqualTo("ana");
        assertThat(stored.ignoredAt()).isEqualTo(at);
        assertThat(stored.status()).isEqualTo(TrackedMatchStatus.OVERDUE);
        assertThat(events()).filteredOn(e -> e.kind() == MatchDayEventKind.MATCH_IGNORED).singleElement()
                .satisfies(event -> {
                    assertThat(event.matchId()).isEqualTo(overdue.matchId());
                    assertThat(event.actor()).isEqualTo("ana");
                    assertThat(event.note()).isEqualTo("forfeit pending");
                });
    }

    @Test
    void ignoringTheLastUnresolvedMatchClosesTheDayAsAllResolved() {
        actions.ignoreMatch(day.id(), overdue.matchId(), "ana", null);

        assertThat(stored().state()).isEqualTo(MatchDayState.CLOSED);
        assertThat(stored().closeReason()).isEqualTo(CloseReason.ALL_RESOLVED);
        assertThat(stored().closedBy()).isEqualTo(MatchDayEvent.SYSTEM_ACTOR);
        assertThat(events()).extracting(MatchDayEvent::kind, MatchDayEvent::actor)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(MatchDayEventKind.MATCH_IGNORED, "ana"),
                        org.assertj.core.api.Assertions.tuple(MatchDayEventKind.CLOSED, MatchDayEvent.SYSTEM_ACTOR));
    }

    @Test
    void ignoringAMatchWhileOthersAreUnresolvedKeepsTheDayOpen() {
        MatchTracking another = TrackerFixtures.match(day.id(), TrackedMatchStatus.AWAITING_RESULT);
        repository.apply(new MatchDayChangeSet(List.of(stored()), List.of(another), java.util.Set.of(), List.of()));

        actions.ignoreMatch(day.id(), overdue.matchId(), "ana", null);

        assertThat(stored().state()).isEqualTo(MatchDayState.OPEN);
    }

    @Test
    void unignoreReopensADayTheTrackerClosed() {
        actions.ignoreMatch(day.id(), overdue.matchId(), "ana", null);

        actions.unignoreMatch(day.id(), overdue.matchId(), "bea", "still pending");

        assertThat(repository.findMatch(overdue.matchId()).orElseThrow().isIgnored()).isFalse();
        assertThat(stored().state()).isEqualTo(MatchDayState.OPEN);
        assertThat(events()).extracting(MatchDayEvent::kind).containsExactly(
                MatchDayEventKind.MATCH_IGNORED, MatchDayEventKind.CLOSED, MatchDayEventKind.MATCH_UNIGNORED,
                MatchDayEventKind.REOPENED);
    }

    @Test
    void unignoreDoesNotReopenAManuallyClosedDay() {
        actions.ignoreMatch(day.id(), overdue.matchId(), "ana", null);
        actions.reopen(day.id(), "ana", null);
        actions.close(day.id(), "ana", null);

        actions.unignoreMatch(day.id(), overdue.matchId(), "ana", null);

        assertThat(stored().state()).isEqualTo(MatchDayState.CLOSED);
    }

    @Test
    void ignoreAndUnignoreRejectWrongMatchState() {
        actions.ignoreMatch(day.id(), overdue.matchId(), "ana", null);

        assertThatThrownBy(() -> actions.ignoreMatch(day.id(), overdue.matchId(), "ana", null))
                .isInstanceOf(IllegalMatchDayTransitionException.class);
        assertThatThrownBy(() -> actions.unignoreMatch(day.id(), reported.matchId(), "ana", null))
                .isInstanceOf(IllegalMatchDayTransitionException.class);
    }

    @Test
    void notesAreRecordedOnTheDayAndOnAMatch() {
        actions.addNote(day.id(), null, "ana", "called the club");
        actions.addNote(day.id(), overdue.matchId(), "bea", "result by phone");

        assertThat(events()).extracting(MatchDayEvent::kind).containsOnly(MatchDayEventKind.NOTE);
        assertThat(events()).extracting(MatchDayEvent::matchId).containsExactly(null, overdue.matchId());
        assertThat(events()).extracting(MatchDayEvent::note).containsExactly("called the club", "result by phone");
        assertThat(stored().state()).isEqualTo(MatchDayState.OPEN);
    }

    @Test
    void blankNoteIsRejected() {
        assertThatThrownBy(() -> actions.addNote(day.id(), null, "ana", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(events()).isEmpty();
    }

    @Test
    void unknownIdsAreNotFound() {
        UUID unknown = UUID.randomUUID();
        MatchDay otherDay = MatchDay.create(
                UUID.randomUUID(), TrackerFixtures.key(2), MatchDayWindow.undated(2), TrackerFixtures.NOW);
        MatchTracking foreign = TrackerFixtures.match(otherDay.id(), TrackedMatchStatus.OVERDUE);
        repository.apply(new MatchDayChangeSet(List.of(otherDay), List.of(foreign), java.util.Set.of(), List.of()));

        assertThatThrownBy(() -> actions.close(unknown, "ana", null)).isInstanceOf(MatchDayNotFoundException.class);
        assertThatThrownBy(() -> actions.reopen(unknown, "ana", null)).isInstanceOf(MatchDayNotFoundException.class);
        assertThatThrownBy(() -> actions.ignoreMatch(day.id(), unknown, "ana", null))
                .isInstanceOf(MatchDayNotFoundException.class);
        assertThatThrownBy(() -> actions.ignoreMatch(day.id(), foreign.matchId(), "ana", null))
                .isInstanceOf(MatchDayNotFoundException.class);
        assertThatThrownBy(() -> actions.addNote(day.id(), foreign.matchId(), "ana", "x"))
                .isInstanceOf(MatchDayNotFoundException.class);
    }

    @Test
    void recordRefreshAppendsOnlyAnEventWithTheRun() {
        UUID runId = UUID.randomUUID();
        MatchDay before = stored();

        actions.recordRefresh(day.id(), "ana", runId, null);

        assertThat(stored().state()).isEqualTo(before.state());
        assertThat(events()).singleElement().satisfies(event -> {
            assertThat(event.kind()).isEqualTo(MatchDayEventKind.REFRESH_REQUESTED);
            assertThat(event.actor()).isEqualTo("ana");
            assertThat(event.runId()).isEqualTo(runId);
            assertThat(event.matchId()).isNull();
        });
    }

    @Test
    void recordRefreshWithoutARunKeepsTheNoteAndWorksOnClosedDays() {
        actions.close(day.id(), "ana", null);

        actions.recordRefresh(day.id(), "bea", null, "Queued behind active run 1");

        assertThat(stored().state()).isEqualTo(MatchDayState.CLOSED);
        assertThat(events()).last().satisfies(event -> {
            assertThat(event.kind()).isEqualTo(MatchDayEventKind.REFRESH_REQUESTED);
            assertThat(event.runId()).isNull();
            assertThat(event.note()).isEqualTo("Queued behind active run 1");
        });
    }

    @Test
    void recordRefreshOfAnUnknownDayIsNotFound() {
        assertThatThrownBy(() -> actions.recordRefresh(UUID.randomUUID(), "ana", null, null))
                .isInstanceOf(MatchDayNotFoundException.class);
    }

    @Test
    void staleVersionIsRejectedByTheRepository() {
        MatchDay loaded = stored();
        actions.addNote(day.id(), null, "ana", "touch");
        actions.close(day.id(), "ana", null);
        MatchDay staleClose = loaded.close(CloseReason.MANUAL, "bea", clock.now());

        assertThatThrownBy(() -> repository.apply(
                new MatchDayChangeSet(List.of(staleClose), List.of(), java.util.Set.of(), List.of())))
                .isInstanceOf(StaleMatchDayException.class);
    }
}
