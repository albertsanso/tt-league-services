package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.MADRID;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.arrival;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.at;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.ignored;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.pending;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.run;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.withDayState;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.junit.jupiter.api.Test;

class StatisticsRulesTest {

    private static final Instant PLAYED = at("2026-10-03T16:00:00Z");

    @Test
    void arrivalNeedsAReportAfterTheFirstSighting() {
        assertThat(StatisticsRules.isArrival(arrival(PipelineSource.FCTT, "A", PLAYED, at("2026-10-03T18:00:00Z"),
                at("2026-10-04T08:00:00Z")))).isTrue();
        // first seen already reported: reportedAt is not after firstSeenAt
        assertThat(StatisticsRules.isArrival(arrival(PipelineSource.FCTT, "A", PLAYED, at("2026-10-04T08:00:00Z"),
                at("2026-10-04T08:00:00Z")))).isFalse();
        assertThat(StatisticsRules.isArrival(arrival(PipelineSource.FCTT, "A", PLAYED, at("2026-10-04T09:00:00Z"),
                at("2026-10-04T08:00:00Z")))).isFalse();
        assertThat(StatisticsRules.isArrival(pending(PipelineSource.FCTT, TrackedMatchStatus.OVERDUE, PLAYED)))
                .isFalse();
    }

    @Test
    void timeToReportExcludesFirstSeenReportedUndatedAndNegativeDurations() {
        Instant seen = at("2026-10-03T18:00:00Z");
        assertThat(StatisticsRules.timeToReport(arrival(PipelineSource.FCTT, "A", PLAYED, seen,
                at("2026-10-04T16:00:00Z")))).contains(Duration.ofHours(24));
        assertThat(StatisticsRules.timeToReport(arrival(PipelineSource.FCTT, "A", PLAYED, seen, PLAYED))).isEmpty();
        // reported before the (rescheduled) match date: negative, left out
        assertThat(StatisticsRules.timeToReport(arrival(PipelineSource.FCTT, "A", at("2026-10-05T16:00:00Z"), seen,
                at("2026-10-04T16:00:00Z")))).isEmpty();
        assertThat(StatisticsRules.timeToReport(arrival(PipelineSource.FCTT, "A", null, seen,
                at("2026-10-04T16:00:00Z")))).isEmpty();
        assertThat(StatisticsRules.timeToReport(arrival(PipelineSource.FCTT, "A", PLAYED, seen, seen))).isEmpty();
    }

    @Test
    void timeToReportIsZeroWhenReportedExactlyAtTheMatchDate() {
        assertThat(StatisticsRules.timeToReport(arrival(PipelineSource.FCTT, "A", PLAYED, PLAYED.minusSeconds(60),
                PLAYED))).contains(Duration.ZERO);
    }

    @Test
    void percentileIsTheNearestRank() {
        List<Duration> one = List.of(Duration.ofHours(5));
        assertThat(StatisticsRules.percentile(one, 0.5)).isEqualTo(Duration.ofHours(5));
        assertThat(StatisticsRules.percentile(one, 0.9)).isEqualTo(Duration.ofHours(5));

        List<Duration> two = List.of(Duration.ofHours(1), Duration.ofHours(9));
        assertThat(StatisticsRules.percentile(two, 0.5)).isEqualTo(Duration.ofHours(1));
        assertThat(StatisticsRules.percentile(two, 0.9)).isEqualTo(Duration.ofHours(9));

        List<Duration> ten = java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(Duration::ofHours).toList();
        assertThat(StatisticsRules.percentile(ten, 0.5)).isEqualTo(Duration.ofHours(5));
        assertThat(StatisticsRules.percentile(ten, 0.9)).isEqualTo(Duration.ofHours(9));
        assertThat(StatisticsRules.percentile(ten, 1.0)).isEqualTo(Duration.ofHours(10));
    }

    @Test
    void percentileRejectsEmptyInputAndBadRanks() {
        assertThatThrownBy(() -> StatisticsRules.percentile(List.of(), 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StatisticsRules.percentile(List.of(Duration.ZERO), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StatisticsRules.percentile(List.of(Duration.ZERO), 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ageBucketsIncludeTheirLowerBound() {
        Instant now = at("2026-10-10T16:00:00Z");
        assertThat(StatisticsRules.ageBucket(now, now)).isEqualTo(AgeBucket.UNDER_1_DAY);
        assertThat(StatisticsRules.ageBucket(now.minus(Duration.ofDays(1)).plusSeconds(1), now))
                .isEqualTo(AgeBucket.UNDER_1_DAY);
        assertThat(StatisticsRules.ageBucket(now.minus(Duration.ofDays(1)), now)).isEqualTo(AgeBucket.DAYS_1_TO_2);
        assertThat(StatisticsRules.ageBucket(now.minus(Duration.ofDays(2)).plusSeconds(1), now))
                .isEqualTo(AgeBucket.DAYS_1_TO_2);
        assertThat(StatisticsRules.ageBucket(now.minus(Duration.ofDays(2)), now)).isEqualTo(AgeBucket.DAYS_2_TO_7);
        assertThat(StatisticsRules.ageBucket(now.minus(Duration.ofDays(7)).plusSeconds(1), now))
                .isEqualTo(AgeBucket.DAYS_2_TO_7);
        assertThat(StatisticsRules.ageBucket(now.minus(Duration.ofDays(7)), now)).isEqualTo(AgeBucket.OVER_7_DAYS);
    }

    @Test
    void pendingNowCoversWaitingStatusesOfUnclosedDaysWithADatePastDue() {
        Instant now = at("2026-10-04T10:00:00Z");
        for (TrackedMatchStatus status : List.of(TrackedMatchStatus.SCHEDULED, TrackedMatchStatus.AWAITING_RESULT,
                TrackedMatchStatus.OVERDUE)) {
            assertThat(StatisticsRules.isPendingNow(pending(PipelineSource.FCTT, status, PLAYED), now)).isTrue();
        }
        assertThat(StatisticsRules.isPendingNow(pending(PipelineSource.FCTT, TrackedMatchStatus.POSTPONED, PLAYED),
                now)).isFalse();
        assertThat(StatisticsRules.isPendingNow(arrival(PipelineSource.FCTT, "A", PLAYED, PLAYED, now), now))
                .isFalse();
        MatchFacts waiting = pending(PipelineSource.FCTT, TrackedMatchStatus.AWAITING_RESULT, PLAYED);
        assertThat(StatisticsRules.isPendingNow(withDayState(waiting, MatchDayState.CLOSED), now)).isFalse();
        assertThat(StatisticsRules.isPendingNow(withDayState(waiting, MatchDayState.UPCOMING), now)).isTrue();
        assertThat(StatisticsRules.isPendingNow(ignored(waiting), now)).isFalse();
        // dated exactly now is pending, dated in the future is not
        assertThat(StatisticsRules.isPendingNow(pending(PipelineSource.FCTT, TrackedMatchStatus.SCHEDULED, now), now))
                .isTrue();
        assertThat(StatisticsRules.isPendingNow(pending(PipelineSource.FCTT, TrackedMatchStatus.SCHEDULED,
                now.plusSeconds(1)), now)).isFalse();
    }

    @Test
    void pendingNowLeavesUndatedMatchesOut() {
        MatchFacts undated = new MatchFacts(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                PipelineSource.FCTT, "2026-2027", "A", MatchDayState.OPEN, TrackedMatchStatus.SCHEDULED, null,
                PLAYED, null, false);
        assertThat(StatisticsRules.isPendingNow(undated, at("2026-10-04T10:00:00Z"))).isFalse();
        assertThat(StatisticsRules.isPendingAt(undated, at("2026-10-05T00:00:00Z"))).isFalse();
    }

    @Test
    void pendingAtEndOfDayExcludesPostponedIgnoredClosedAndAlreadyReported() {
        Instant endOfDay = at("2026-10-03T22:00:00Z");
        MatchFacts waiting = pending(PipelineSource.FCTT, TrackedMatchStatus.AWAITING_RESULT, PLAYED);
        assertThat(StatisticsRules.isPendingAt(waiting, endOfDay)).isTrue();
        assertThat(StatisticsRules.isPendingAt(pending(PipelineSource.FCTT, TrackedMatchStatus.POSTPONED, PLAYED),
                endOfDay)).isFalse();
        assertThat(StatisticsRules.isPendingAt(ignored(waiting), endOfDay)).isFalse();
        assertThat(StatisticsRules.isPendingAt(withDayState(waiting, MatchDayState.CLOSED), endOfDay)).isFalse();
        // dated after the end of the day
        assertThat(StatisticsRules.isPendingAt(pending(PipelineSource.FCTT, TrackedMatchStatus.SCHEDULED, endOfDay),
                endOfDay)).isFalse();
        // reported before the end of the day vs at or after it
        assertThat(StatisticsRules.isPendingAt(arrival(PipelineSource.FCTT, "A", PLAYED, PLAYED.minusSeconds(60),
                endOfDay.minusSeconds(1)), endOfDay)).isFalse();
        assertThat(StatisticsRules.isPendingAt(arrival(PipelineSource.FCTT, "A", PLAYED, PLAYED.minusSeconds(60),
                endOfDay), endOfDay)).isTrue();
    }

    @Test
    void dailyStatsCountsTheDayRunsFailuresArrivalsAndPending() {
        LocalDate day = LocalDate.parse("2026-10-03");
        Instant start = day.atStartOfDay(MADRID).toInstant();
        List<RunFacts> runs = List.of(
                run(PipelineSource.FCTT, RunStatus.SUCCEEDED, start.plusSeconds(3600)),
                run(PipelineSource.FCTT, RunStatus.FAILED, start.plusSeconds(7200)),
                run(PipelineSource.FCTT, RunStatus.NO_CHANGES, start.minusSeconds(1)),
                run(PipelineSource.FCTT, RunStatus.NO_CHANGES, start.plus(Duration.ofDays(1))),
                run(PipelineSource.RFETM, RunStatus.FAILED, start.plusSeconds(10)));
        List<MatchFacts> matches = List.of(
                arrival(PipelineSource.FCTT, "A", start.minus(Duration.ofHours(10)), start.minus(Duration.ofHours(8)),
                        start.plus(Duration.ofHours(2))),
                arrival(PipelineSource.FCTT, "A", start.minus(Duration.ofHours(6)), start.minus(Duration.ofHours(4)),
                        start.plus(Duration.ofHours(8))),
                // first seen already reported on the day: not an arrival
                arrival(PipelineSource.FCTT, "A", start.plusSeconds(60), start.plus(Duration.ofHours(5)),
                        start.plus(Duration.ofHours(5))),
                pending(PipelineSource.FCTT, TrackedMatchStatus.AWAITING_RESULT, start.plus(Duration.ofHours(12))),
                pending(PipelineSource.RFETM, TrackedMatchStatus.OVERDUE, start.plus(Duration.ofHours(12))));

        DailyStats fctt = StatisticsRules.dailyStats(PipelineSource.FCTT, day, runs, matches, MADRID,
                start.plus(Duration.ofDays(2)));

        assertThat(fctt.runs()).isEqualTo(2);
        assertThat(fctt.failures()).isEqualTo(1);
        assertThat(fctt.matchesReported()).isEqualTo(2);
        assertThat(fctt.avgTimeToReport()).isEqualTo(Duration.ofHours(13));
        assertThat(fctt.pendingEndOfDay()).isEqualTo(1);
    }

    @Test
    void dailyStatsWithoutDataIsAZeroRowWithoutAnAverage() {
        DailyStats stats = StatisticsRules.dailyStats(PipelineSource.BCNESA, LocalDate.parse("2026-10-03"), List.of(),
                List.of(), MADRID, at("2026-10-05T00:00:00Z"));
        assertThat(stats.runs()).isZero();
        assertThat(stats.failures()).isZero();
        assertThat(stats.matchesReported()).isZero();
        assertThat(stats.avgTimeToReport()).isNull();
        assertThat(stats.pendingEndOfDay()).isZero();
    }

    @Test
    void dayBoundariesFollowTheZoneAcrossTheAutumnDstChange() {
        // 2026-10-25 has 25 hours in Europe/Madrid: 22:00Z on the 24th to 23:00Z on the 25th
        LocalDate day = LocalDate.parse("2026-10-25");
        List<RunFacts> runs = List.of(
                run(PipelineSource.FCTT, RunStatus.SUCCEEDED, at("2026-10-24T21:59:59Z")),
                run(PipelineSource.FCTT, RunStatus.SUCCEEDED, at("2026-10-24T22:00:00Z")),
                run(PipelineSource.FCTT, RunStatus.SUCCEEDED, at("2026-10-25T22:59:59Z")),
                run(PipelineSource.FCTT, RunStatus.SUCCEEDED, at("2026-10-25T23:00:00Z")));

        DailyStats stats = StatisticsRules.dailyStats(PipelineSource.FCTT, day, runs, List.of(), MADRID,
                at("2026-10-27T00:00:00Z"));

        assertThat(stats.runs()).isEqualTo(2);
        assertThat(StatisticsRules.isOnDay(at("2026-10-25T22:59:59Z"), day, MADRID)).isTrue();
        assertThat(StatisticsRules.isOnDay(at("2026-10-25T23:00:00Z"), day, MADRID)).isFalse();
    }

    @Test
    void dayBoundariesFollowTheZoneAcrossTheSpringDstChange() {
        // 2027-03-28 has 23 hours: 23:00Z on the 27th to 22:00Z on the 28th
        LocalDate day = LocalDate.parse("2027-03-28");
        assertThat(StatisticsRules.startOf(day, MADRID)).isEqualTo(at("2027-03-27T23:00:00Z"));
        assertThat(StatisticsRules.startOf(day.plusDays(1), MADRID)).isEqualTo(at("2027-03-28T22:00:00Z"));
    }

    @Test
    void meanRoundsToTheSecond() {
        assertThat(StatisticsRules.mean(List.of())).isNull();
        assertThat(StatisticsRules.mean(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2))))
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(StatisticsRules.mean(List.of(Duration.ofHours(1), Duration.ofHours(3))))
                .isEqualTo(Duration.ofHours(2));
    }
}
