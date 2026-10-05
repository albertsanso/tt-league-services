package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.SETTINGS;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.arrival;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.at;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.ingestStep;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.pending;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.run;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryDailyStatsRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryStatisticsReadRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.junit.jupiter.api.Test;

class StatisticsQueriesTest {

    // 2026-10-10 10:00 in Madrid
    private static final Instant NOW = at("2026-10-10T08:00:00Z");
    private static final DateRange WEEK = new DateRange(LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-10"));

    private final InMemoryStatisticsReadRepository reads = new InMemoryStatisticsReadRepository();
    private final InMemoryDailyStatsRepository daily = new InMemoryDailyStatsRepository();
    private final InMemoryMatchDayRepository matchDays = new InMemoryMatchDayRepository();
    private final FakeRunClock clock = new FakeRunClock(NOW);
    private final StatisticsQueries queries = new StatisticsQueries(reads, daily, matchDays, clock, SETTINGS);

    @Test
    void dateRangeIsInclusiveOrderedAndBounded() {
        assertThat(WEEK.contains(LocalDate.parse("2026-10-04"))).isTrue();
        assertThat(WEEK.contains(LocalDate.parse("2026-10-10"))).isTrue();
        assertThat(WEEK.contains(LocalDate.parse("2026-10-11"))).isFalse();
        assertThat(new DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-01")).from())
                .isEqualTo(LocalDate.parse("2026-01-01"));
        assertThatThrownBy(() -> new DateRange(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-04")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DateRange(LocalDate.parse("2025-09-30"), LocalDate.parse("2026-10-01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01")).to())
                .isEqualTo(LocalDate.parse("2027-01-01"));
    }

    @Test
    void dateRangeInstantsFollowTheZone() {
        assertThat(WEEK.startInstant(SETTINGS.zone())).isEqualTo(at("2026-10-03T22:00:00Z"));
        assertThat(WEEK.endInstant(SETTINGS.zone())).isEqualTo(at("2026-10-10T22:00:00Z"));
    }

    @Test
    void settingsRejectAnOutOfRangeBackfill() {
        assertThatThrownBy(() -> new StatisticsSettings(SETTINGS.zone(), SETTINGS.dailyAt(), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StatisticsSettings(SETTINGS.zone(), SETTINGS.dailyAt(), 367))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dailyReturnsTheStoredRowsOfTheRangeAndSources() {
        new DailyStatsAggregator(reads, daily, clock, SETTINGS).catchUp();

        assertThat(queries.daily(WEEK, Set.of(PipelineSource.FCTT)))
                .hasSize(6)
                .allSatisfy(row -> assertThat(row.source()).isEqualTo(PipelineSource.FCTT));
        assertThat(queries.daily(WEEK, Set.of())).hasSize(18);
    }

    @Test
    void runsCountOutcomesPerDayAndSourceAndAverageTheStepDurations() {
        reads.add(run(PipelineSource.FCTT, RunStatus.SUCCEEDED, at("2026-10-05T10:00:00Z")))
                .add(run(PipelineSource.FCTT, RunStatus.FAILED, at("2026-10-05T11:00:00Z")))
                .add(run(PipelineSource.FCTT, RunStatus.NO_CHANGES, at("2026-10-05T12:00:00Z")))
                .add(run(PipelineSource.FCTT, RunStatus.PARTIAL, at("2026-10-05T13:00:00Z")))
                .add(run(PipelineSource.RFETM, RunStatus.SUCCEEDED, at("2026-10-06T10:00:00Z")))
                .add(run(PipelineSource.FCTT, RunStatus.SUCCEEDED, at("2026-09-01T10:00:00Z")));
        reads.add(step(PipelineSource.FCTT, StepKind.INGEST, StepStatus.SUCCEEDED, 60))
                .add(step(PipelineSource.FCTT, StepKind.INGEST, StepStatus.FAILED, 120))
                .add(step(PipelineSource.FCTT, StepKind.IMPORT, StepStatus.SUCCEEDED, 10));

        RunOutcomeStats stats = queries.runs(WEEK, Set.of());

        assertThat(stats.days()).containsExactly(
                new RunOutcomeStats.DayOutcomes(LocalDate.parse("2026-10-05"), PipelineSource.FCTT, 1, 1, 1, 1),
                new RunOutcomeStats.DayOutcomes(LocalDate.parse("2026-10-06"), PipelineSource.RFETM, 1, 0, 0, 0));
        assertThat(stats.stepAverages()).containsExactly(
                new RunOutcomeStats.StepAverage(PipelineSource.FCTT, StepKind.INGEST, 2, Duration.ofSeconds(90)),
                new RunOutcomeStats.StepAverage(PipelineSource.FCTT, StepKind.IMPORT, 1, Duration.ofSeconds(10)));
    }

    @Test
    void timeToReportGivesMedianAndP90PerSourceCompetitionAndSourceTotal() {
        Instant played = at("2026-10-03T16:00:00Z");
        Instant seen = at("2026-10-03T17:00:00Z");
        for (int hours : List.of(2, 4, 6, 8, 10)) {
            reads.add(arrival(PipelineSource.FCTT, "TERCERA", played, seen, played.plus(Duration.ofHours(hours))));
        }
        reads.add(arrival(PipelineSource.FCTT, "PREFERENT", played, seen, played.plus(Duration.ofHours(20))));
        // first seen already reported: not part of any figure
        reads.add(arrival(PipelineSource.FCTT, "TERCERA", played, seen, seen));
        reads.add(arrival(PipelineSource.RFETM, "HONOR", played, seen, played.plus(Duration.ofHours(2))));

        TimeToReportStats stats = queries.timeToReport("2026-2027", Set.of());

        assertThat(stats.rows()).containsExactly(
                new TimeToReportStats.Row(PipelineSource.RFETM, null, 1, Duration.ofHours(2), Duration.ofHours(2)),
                new TimeToReportStats.Row(PipelineSource.RFETM, "HONOR", 1, Duration.ofHours(2), Duration.ofHours(2)),
                new TimeToReportStats.Row(PipelineSource.FCTT, null, 6, Duration.ofHours(6), Duration.ofHours(20)),
                new TimeToReportStats.Row(PipelineSource.FCTT, "PREFERENT", 1, Duration.ofHours(20),
                        Duration.ofHours(20)),
                new TimeToReportStats.Row(PipelineSource.FCTT, "TERCERA", 5, Duration.ofHours(6), Duration.ofHours(10)));
    }

    @Test
    void timeToReportNeedsAValidSeasonAndFiltersSources() {
        assertThatThrownBy(() -> queries.timeToReport("26-27", Set.of()))
                .isInstanceOf(RuntimeException.class);
        assertThat(queries.timeToReport("2026-2027", Set.of(PipelineSource.BCNESA)).rows()).isEmpty();
    }

    @Test
    void pendingBucketsByAgeAndCountsOverdue() {
        reads.add(pending(PipelineSource.FCTT, TrackedMatchStatus.SCHEDULED, NOW.minus(Duration.ofHours(5))))
                .add(pending(PipelineSource.FCTT, TrackedMatchStatus.AWAITING_RESULT, NOW.minus(Duration.ofDays(1))))
                .add(pending(PipelineSource.FCTT, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(3))))
                .add(pending(PipelineSource.FCTT, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(9))))
                .add(pending(PipelineSource.FCTT, TrackedMatchStatus.POSTPONED, NOW.minus(Duration.ofDays(9))))
                .add(pending(PipelineSource.FCTT, TrackedMatchStatus.SCHEDULED, NOW.plus(Duration.ofDays(1))))
                .add(pending(PipelineSource.RFETM, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(2))));

        PendingByAge result = queries.pending(Set.of(), null);

        assertThat(result.asOf()).isEqualTo(NOW);
        assertThat(result.sources()).containsExactly(
                new PendingByAge.SourcePending(PipelineSource.RFETM, 0, 0, 1, 0, 1),
                new PendingByAge.SourcePending(PipelineSource.BCNESA, 0, 0, 0, 0, 0),
                new PendingByAge.SourcePending(PipelineSource.FCTT, 1, 1, 1, 1, 2));
        assertThat(queries.pending(Set.of(PipelineSource.FCTT), "2026-2027").sources()).hasSize(1);
        assertThat(queries.pending(Set.of(PipelineSource.FCTT), "2025-2026").sources())
                .containsExactly(new PendingByAge.SourcePending(PipelineSource.FCTT, 0, 0, 0, 0, 0));
    }

    @Test
    void correctionsSumPerDayAndSource() {
        reads.add(new CorrectionFacts(UUID.randomUUID(), PipelineSource.FCTT, at("2026-10-05T10:00:00Z"), 2))
                .add(new CorrectionFacts(UUID.randomUUID(), PipelineSource.FCTT, at("2026-10-05T12:00:00Z"), 1))
                .add(new CorrectionFacts(UUID.randomUUID(), PipelineSource.FCTT, at("2026-10-07T12:00:00Z"), 4))
                .add(new CorrectionFacts(UUID.randomUUID(), PipelineSource.RFETM, at("2026-10-07T12:00:00Z"), 0))
                .add(new CorrectionFacts(UUID.randomUUID(), PipelineSource.FCTT, at("2026-09-07T12:00:00Z"), 9));

        CorrectionStats stats = queries.corrections(WEEK, Set.of());

        assertThat(stats.days()).containsExactly(
                new CorrectionStats.DayCorrections(LocalDate.parse("2026-10-05"), PipelineSource.FCTT, 3),
                new CorrectionStats.DayCorrections(LocalDate.parse("2026-10-07"), PipelineSource.RFETM, 0),
                new CorrectionStats.DayCorrections(LocalDate.parse("2026-10-07"), PipelineSource.FCTT, 4));
        assertThat(stats.totals()).containsExactly(
                new CorrectionStats.SourceCorrections(PipelineSource.RFETM, 0),
                new CorrectionStats.SourceCorrections(PipelineSource.FCTT, 7));
    }

    @Test
    void sourceHealthSumsTheIngestAttemptsAndCountsUnknownHealth() {
        reads.add(ingestStep(PipelineSource.FCTT, "SUCCEEDED", at("2026-10-05T10:00:00Z"), new IngestHealth(2, 1, 0)))
                .add(ingestStep(PipelineSource.FCTT, "SOURCE_UNAVAILABLE", at("2026-10-05T11:00:00Z"),
                        new IngestHealth(5, 3, 1)))
                .add(ingestStep(PipelineSource.FCTT, "NO_CHANGES", at("2026-10-06T11:00:00Z"), null))
                .add(step(PipelineSource.FCTT, StepKind.IMPORT, StepStatus.SUCCEEDED, 10));

        SourceHealthStats stats = queries.sourceHealth(WEEK, Set.of(PipelineSource.FCTT));

        assertThat(stats.days()).containsExactly(
                new SourceHealthStats.DayHealth(LocalDate.parse("2026-10-05"), PipelineSource.FCTT, 7, 4, 1, 2, 1, 0),
                new SourceHealthStats.DayHealth(LocalDate.parse("2026-10-06"), PipelineSource.FCTT, 0, 0, 0, 1, 0, 1));
        assertThat(stats.totals()).containsExactly(
                new SourceHealthStats.SourceHealth(PipelineSource.FCTT, 7, 4, 1, 3, 1, 1));
    }

    @Test
    void reportingProgressBuildsOnePointPerDateAndLeavesIgnoredAndUndatedDaysOut() {
        MatchDay open = matchDay("TERCERA", 1, new MatchDayWindow(LocalDate.parse("2026-10-08"),
                LocalDate.parse("2026-10-09"), 2)).open(NOW);
        MatchDay undated = matchDay("TERCERA", 2, MatchDayWindow.undated(2));
        MatchDay other = matchDay("PREFERENT", 1, new MatchDayWindow(LocalDate.parse("2026-10-08"),
                LocalDate.parse("2026-10-08"), 2));
        matchDays.apply(new MatchDayChangeSet(List.of(open, undated, other),
                List.of(
                        match(open, TrackedMatchStatus.REPORTED, at("2026-10-08T18:00:00Z")),
                        match(open, TrackedMatchStatus.REPORTED, at("2026-10-09T18:00:00Z")),
                        match(open, TrackedMatchStatus.POSTPONED, null),
                        match(open, TrackedMatchStatus.AWAITING_RESULT, null),
                        match(open, TrackedMatchStatus.OVERDUE, null).ignore("admin", NOW),
                        match(other, TrackedMatchStatus.AWAITING_RESULT, null)),
                Set.of(), List.of()));

        List<ReportingProgress> rows = queries.reportingProgress(PipelineSource.FCTT, "2026-2027", "TERCERA");

        assertThat(rows).hasSize(1);
        ReportingProgress progress = rows.get(0);
        assertThat(progress.state()).isEqualTo(MatchDayState.OPEN);
        assertThat(progress.windowStart()).isEqualTo(LocalDate.parse("2026-10-08"));
        assertThat(progress.windowEnd()).isEqualTo(LocalDate.parse("2026-10-11"));
        assertThat(progress.active()).isEqualTo(4);
        assertThat(progress.reported()).isEqualTo(2);
        assertThat(progress.postponed()).isEqualTo(1);
        assertThat(progress.pending()).isEqualTo(1);
        // today is 2026-10-10 in Madrid, before the window end
        assertThat(progress.points()).containsExactly(
                new ReportingProgress.Point(LocalDate.parse("2026-10-08"), 1, 2),
                new ReportingProgress.Point(LocalDate.parse("2026-10-09"), 2, 1),
                new ReportingProgress.Point(LocalDate.parse("2026-10-10"), 2, 1));
        assertThat(queries.reportingProgress(PipelineSource.FCTT, "2026-2027", null)).hasSize(2);
        assertThat(queries.reportingProgress(PipelineSource.RFETM, "2026-2027", null)).isEmpty();
    }

    @Test
    void reportingProgressStopsAtTheWindowEndOnceItIsPast() {
        MatchDay old = matchDay("TERCERA", 1, new MatchDayWindow(LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-01"), 1));
        matchDays.apply(new MatchDayChangeSet(List.of(old), List.of(), Set.of(), List.of()));

        ReportingProgress progress = queries.reportingProgress(PipelineSource.FCTT, "2026-2027", null).get(0);

        assertThat(progress.points()).extracting(ReportingProgress.Point::date)
                .containsExactly(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-02"));
        assertThat(progress.active()).isZero();
    }

    private static StepFacts step(PipelineSource source, StepKind kind, StepStatus status, long seconds) {
        Instant finished = at("2026-10-06T10:00:00Z");
        return new StepFacts(UUID.randomUUID(), source, kind, status, null, finished.minusSeconds(seconds), finished,
                null);
    }

    private static MatchDay matchDay(String competition, int round, MatchDayWindow window) {
        return MatchDay.create(UUID.randomUUID(),
                new MatchDayKey(PipelineSource.FCTT, "2026-2027", competition, 1, "1a Fase", round), window, NOW);
    }

    private static MatchTracking match(MatchDay day, TrackedMatchStatus status, Instant reportedAt) {
        Instant seen = at("2026-10-08T10:00:00Z");
        return MatchTracking.first(UUID.randomUUID(), day.id(), status, at("2026-10-08T16:00:00Z"), "Home", "Away",
                seen, reportedAt, null);
    }
}
