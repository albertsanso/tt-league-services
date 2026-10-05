package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackerFixtures.TODAY;
import static org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackerFixtures.match;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrackerRulesTest {

    private static Map<TrackedMatchStatus, Integer> counts(Object... pairs) {
        Map<TrackedMatchStatus, Integer> counts = new EnumMap<>(TrackedMatchStatus.class);
        for (int i = 0; i < pairs.length; i += 2) {
            counts.put((TrackedMatchStatus) pairs[i], (Integer) pairs[i + 1]);
        }
        return counts;
    }

    @Test
    void upcomingDaysAreFutureWhateverTheCounts() {
        assertThat(TrackerRules.completion(MatchDayState.UPCOMING, counts(TrackedMatchStatus.SCHEDULED, 4)))
                .isEqualTo(MatchDayCompletion.FUTURE);
        assertThat(TrackerRules.completion(MatchDayState.UPCOMING, counts(TrackedMatchStatus.OVERDUE, 1)))
                .isEqualTo(MatchDayCompletion.FUTURE);
        assertThat(TrackerRules.completion(MatchDayState.UPCOMING, counts())).isEqualTo(MatchDayCompletion.FUTURE);
    }

    @Test
    void anActiveOverdueMatchMakesAnOpenOrManuallyClosedDayHasOverdue() {
        Map<TrackedMatchStatus, Integer> mixed =
                counts(TrackedMatchStatus.REPORTED, 3, TrackedMatchStatus.OVERDUE, 1, TrackedMatchStatus.SCHEDULED, 2);
        assertThat(TrackerRules.completion(MatchDayState.OPEN, mixed)).isEqualTo(MatchDayCompletion.HAS_OVERDUE);
        assertThat(TrackerRules.completion(MatchDayState.CLOSED, mixed)).isEqualTo(MatchDayCompletion.HAS_OVERDUE);
    }

    @Test
    void awaitingScheduledOrPostponedMatchesAreInProgress() {
        for (TrackedMatchStatus pending : List.of(TrackedMatchStatus.AWAITING_RESULT, TrackedMatchStatus.SCHEDULED,
                TrackedMatchStatus.POSTPONED)) {
            Map<TrackedMatchStatus, Integer> mixed = counts(TrackedMatchStatus.REPORTED, 2, pending, 1);
            assertThat(TrackerRules.completion(MatchDayState.OPEN, mixed)).isEqualTo(MatchDayCompletion.IN_PROGRESS);
            assertThat(TrackerRules.completion(MatchDayState.CLOSED, mixed))
                    .isEqualTo(MatchDayCompletion.IN_PROGRESS);
        }
    }

    @Test
    void everyActiveMatchReportedIsCompleteIncludingEmptyDays() {
        assertThat(TrackerRules.completion(MatchDayState.OPEN, counts(TrackedMatchStatus.REPORTED, 6)))
                .isEqualTo(MatchDayCompletion.COMPLETE);
        assertThat(TrackerRules.completion(MatchDayState.CLOSED, counts(TrackedMatchStatus.REPORTED, 6)))
                .isEqualTo(MatchDayCompletion.COMPLETE);
        assertThat(TrackerRules.completion(MatchDayState.OPEN, counts())).isEqualTo(MatchDayCompletion.COMPLETE);
        assertThat(TrackerRules.completion(MatchDayState.CLOSED, counts())).isEqualTo(MatchDayCompletion.COMPLETE);
    }

    @Test
    void completionRequiresItsArguments() {
        assertThatThrownBy(() -> TrackerRules.completion(null, counts())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackerRules.completion(MatchDayState.OPEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsEveryCalendarState() {
        assertThat(TrackerRules.mapCalendarState("PLAYED")).isEqualTo(TrackedMatchStatus.REPORTED);
        assertThat(TrackerRules.mapCalendarState("AWAITING_RESULT")).isEqualTo(TrackedMatchStatus.AWAITING_RESULT);
        assertThat(TrackerRules.mapCalendarState("OVERDUE")).isEqualTo(TrackedMatchStatus.OVERDUE);
        assertThat(TrackerRules.mapCalendarState("POSTPONED")).isEqualTo(TrackedMatchStatus.POSTPONED);
        assertThat(TrackerRules.mapCalendarState("UPCOMING")).isEqualTo(TrackedMatchStatus.SCHEDULED);
        assertThat(TrackerRules.mapCalendarState("UNDATED")).isEqualTo(TrackedMatchStatus.SCHEDULED);
    }

    @Test
    void unknownCalendarStateIsRejected() {
        assertThatThrownBy(() -> TrackerRules.mapCalendarState("CANCELLED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CANCELLED");
        assertThatThrownBy(() -> TrackerRules.mapCalendarState(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upcomingDayOpensWhenItsWindowStarted() {
        MatchDay started = TrackerFixtures.dayStarted();
        List<MatchTracking> matches = List.of(match(started.id(), TrackedMatchStatus.SCHEDULED));

        assertThat(TrackerRules.targetState(started, matches, TODAY)).isEqualTo(MatchDayState.OPEN);
    }

    @Test
    void upcomingDayStaysUpcomingBeforeItsWindow() {
        MatchDay future = TrackerFixtures.day(new MatchDayWindow(TODAY.plusDays(3), TODAY.plusDays(4), 2));
        List<MatchTracking> matches = List.of(match(future.id(), TrackedMatchStatus.SCHEDULED));

        assertThat(TrackerRules.targetState(future, matches, TODAY)).isEqualTo(MatchDayState.UPCOMING);
    }

    @Test
    void undatedDayOpensOnlyWhenAMatchNeedsAttention() {
        MatchDay undated = TrackerFixtures.day(MatchDayWindow.undated(2));

        assertThat(TrackerRules.targetState(undated, List.of(match(undated.id(), TrackedMatchStatus.SCHEDULED)), TODAY))
                .isEqualTo(MatchDayState.UPCOMING);
        for (TrackedMatchStatus status : List.of(
                TrackedMatchStatus.AWAITING_RESULT, TrackedMatchStatus.OVERDUE)) {
            assertThat(TrackerRules.targetState(undated, List.of(match(undated.id(), status)), TODAY))
                    .as(status.name())
                    .isEqualTo(MatchDayState.OPEN);
        }
    }

    @Test
    void reportedMatchOpensADayBeforeItsWindow() {
        MatchDay future = TrackerFixtures.day(new MatchDayWindow(TODAY.plusDays(3), TODAY.plusDays(4), 2));
        List<MatchTracking> matches = List.of(
                match(future.id(), TrackedMatchStatus.REPORTED), match(future.id(), TrackedMatchStatus.SCHEDULED));

        assertThat(TrackerRules.targetState(future, matches, TODAY)).isEqualTo(MatchDayState.OPEN);
    }

    @Test
    void openDayClosesWhenEveryMatchIsReportedOrPostponed() {
        MatchDay open = TrackerFixtures.openDay();
        List<MatchTracking> matches = List.of(
                match(open.id(), TrackedMatchStatus.REPORTED), match(open.id(), TrackedMatchStatus.POSTPONED));

        assertThat(TrackerRules.canAutoClose(matches)).isTrue();
        assertThat(TrackerRules.targetState(open, matches, TODAY)).isEqualTo(MatchDayState.CLOSED);
    }

    @Test
    void ignoredMatchCountsAsResolved() {
        MatchDay open = TrackerFixtures.openDay();
        MatchTracking ignored = match(open.id(), TrackedMatchStatus.OVERDUE).ignore("ana", TrackerFixtures.NOW);
        List<MatchTracking> matches = List.of(match(open.id(), TrackedMatchStatus.REPORTED), ignored);

        assertThat(TrackerRules.targetState(open, matches, TODAY)).isEqualTo(MatchDayState.CLOSED);
    }

    @Test
    void overdueAwaitingAndScheduledKeepTheDayOpen() {
        MatchDay open = TrackerFixtures.openDay();
        for (TrackedMatchStatus status : List.of(
                TrackedMatchStatus.OVERDUE, TrackedMatchStatus.AWAITING_RESULT, TrackedMatchStatus.SCHEDULED)) {
            List<MatchTracking> matches =
                    List.of(match(open.id(), TrackedMatchStatus.REPORTED), match(open.id(), status));

            assertThat(TrackerRules.canAutoClose(matches)).as(status.name()).isFalse();
            assertThat(TrackerRules.targetState(open, matches, TODAY)).as(status.name()).isEqualTo(MatchDayState.OPEN);
        }
    }

    @Test
    void dayWithoutMatchesNeverAutoCloses() {
        MatchDay open = TrackerFixtures.openDay();

        assertThat(TrackerRules.canAutoClose(List.of())).isFalse();
        assertThat(TrackerRules.targetState(open, List.of(), TODAY)).isEqualTo(MatchDayState.OPEN);
    }

    @Test
    void closedDayStaysClosedInTargetState() {
        MatchDay closed = TrackerFixtures.openDay().close(CloseReason.MANUAL, "ana", TrackerFixtures.NOW);

        assertThat(TrackerRules.targetState(closed, List.of(match(closed.id(), TrackedMatchStatus.OVERDUE)), TODAY))
                .isEqualTo(MatchDayState.CLOSED);
    }

    @Test
    void onlyTrackerClosedDaysReopen() {
        MatchDay allResolved = TrackerFixtures.openDay()
                .close(CloseReason.ALL_RESOLVED, MatchDayEvent.SYSTEM_ACTOR, TrackerFixtures.NOW);
        MatchDay manual = TrackerFixtures.openDay().close(CloseReason.MANUAL, "ana", TrackerFixtures.NOW);
        MatchDay removed = TrackerFixtures.openDay()
                .close(CloseReason.REMOVED, MatchDayEvent.SYSTEM_ACTOR, TrackerFixtures.NOW);

        assertThat(TrackerRules.shouldReopen(allResolved, List.of(match(allResolved.id(), TrackedMatchStatus.OVERDUE))))
                .isTrue();
        assertThat(TrackerRules.shouldReopen(allResolved, List.of(match(allResolved.id(), TrackedMatchStatus.REPORTED))))
                .isFalse();
        assertThat(TrackerRules.shouldReopen(allResolved, List.of())).isFalse();
        assertThat(TrackerRules.shouldReopen(manual, List.of(match(manual.id(), TrackedMatchStatus.OVERDUE))))
                .isFalse();
        assertThat(TrackerRules.shouldReopen(removed, List.of(match(removed.id(), TrackedMatchStatus.OVERDUE))))
                .isFalse();
        assertThat(TrackerRules.shouldReopen(TrackerFixtures.openDay(), List.of())).isFalse();
    }
}
