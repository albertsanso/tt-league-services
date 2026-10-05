package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.SETTINGS;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.at;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.ignored;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.pending;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.withDayState;

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
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.CloseReason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.junit.jupiter.api.Test;

class OperationalGaugesTest {

    private static final Instant NOW = at("2026-10-10T08:00:00Z");

    private final InMemoryStatisticsReadRepository reads = new InMemoryStatisticsReadRepository();
    private final InMemoryMatchDayRepository matchDays = new InMemoryMatchDayRepository();
    private final FakeRunClock clock = new FakeRunClock(NOW);
    private final StatisticsQueries queries = new StatisticsQueries(
            reads, new InMemoryDailyStatsRepository(), matchDays, clock, SETTINGS);
    private final OperationalGauges gauges = new OperationalGauges(queries, matchDays);

    private static MatchDay day(PipelineSource source, int round) {
        MatchDayKey key = new MatchDayKey(source, "2026-2027", "TERCERA", 1, "1a Fase", round);
        return MatchDay.create(UUID.randomUUID(), key,
                new MatchDayWindow(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-09"), 2), NOW);
    }

    @Test
    void emptyInputGivesZeroForEverySource() {
        OperationalReadings readings = gauges.read();

        assertThat(readings.asOf()).isEqualTo(NOW);
        assertThat(readings.pending()).containsOnlyKeys(PipelineSource.values());
        assertThat(readings.openMatchDays()).containsOnlyKeys(PipelineSource.values());
        readings.pending().values().forEach(row ->
                assertThat(List.of(row.under1Day(), row.days1To2(), row.days2To7(), row.over7Days(), row.overdue()))
                        .containsOnly(0));
        assertThat(readings.openMatchDays().values()).containsOnly(0);
    }

    @Test
    void pendingFollowsTheStatisticsRule() {
        reads.add(pending(PipelineSource.FCTT, TrackedMatchStatus.SCHEDULED, NOW.minus(Duration.ofHours(5))))
                .add(pending(PipelineSource.FCTT, TrackedMatchStatus.AWAITING_RESULT, NOW.minus(Duration.ofDays(1))))
                .add(pending(PipelineSource.FCTT, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(3))))
                .add(pending(PipelineSource.FCTT, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(9))))
                .add(pending(PipelineSource.FCTT, TrackedMatchStatus.POSTPONED, NOW.minus(Duration.ofDays(9))))
                .add(ignored(pending(PipelineSource.FCTT, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(2)))))
                .add(withDayState(
                        pending(PipelineSource.FCTT, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(2))),
                        MatchDayState.CLOSED))
                .add(pending(PipelineSource.FCTT, TrackedMatchStatus.SCHEDULED, NOW.plus(Duration.ofDays(1))))
                .add(pending(PipelineSource.RFETM, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(2))));

        OperationalReadings readings = gauges.read();

        assertThat(readings.pending().get(PipelineSource.FCTT))
                .isEqualTo(queries.pending(Set.of(PipelineSource.FCTT), null).sources().get(0));
        assertThat(readings.pending().get(PipelineSource.FCTT).overdue()).isEqualTo(2);
        assertThat(readings.pending().get(PipelineSource.RFETM).days2To7()).isEqualTo(1);
        assertThat(readings.pending().get(PipelineSource.BCNESA).overdue()).isZero();
    }

    @Test
    void countsOnlyOpenMatchDaysPerSource() {
        MatchDay fctt1 = day(PipelineSource.FCTT, 1).open(NOW);
        MatchDay fctt2 = day(PipelineSource.FCTT, 2).open(NOW);
        MatchDay fcttClosed = day(PipelineSource.FCTT, 3).open(NOW).close(CloseReason.MANUAL, "admin", NOW);
        MatchDay fcttUpcoming = day(PipelineSource.FCTT, 4);
        MatchDay rfetm = day(PipelineSource.RFETM, 1).open(NOW);
        matchDays.apply(new MatchDayChangeSet(List.of(fctt1, fctt2, fcttClosed, fcttUpcoming, rfetm),
                List.of(), Set.of(), List.of()));

        OperationalReadings readings = gauges.read();

        assertThat(readings.openMatchDays().get(PipelineSource.FCTT)).isEqualTo(2);
        assertThat(readings.openMatchDays().get(PipelineSource.RFETM)).isEqualTo(1);
        assertThat(readings.openMatchDays().get(PipelineSource.BCNESA)).isZero();
    }
}
