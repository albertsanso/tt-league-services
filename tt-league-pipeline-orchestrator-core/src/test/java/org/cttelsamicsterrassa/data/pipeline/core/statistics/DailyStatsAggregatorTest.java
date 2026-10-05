package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.MADRID;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.SETTINGS;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.at;
import static org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsFixtures.run;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryDailyStatsRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryStatisticsReadRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.junit.jupiter.api.Test;

class DailyStatsAggregatorTest {

    // 2026-10-10 10:00 in Madrid (UTC+2)
    private static final Instant NOW = at("2026-10-10T08:00:00Z");

    private final InMemoryStatisticsReadRepository reads = new InMemoryStatisticsReadRepository();
    private final InMemoryDailyStatsRepository stored = new InMemoryDailyStatsRepository();
    private final FakeRunClock clock = new FakeRunClock(NOW);

    private DailyStatsAggregator aggregator(int backfillDays) {
        return new DailyStatsAggregator(reads, stored, clock,
                new StatisticsSettings(MADRID, LocalTime.of(0, 30), backfillDays));
    }

    @Test
    void catchUpFromEmptyUsesTheBackfillWindowAndStopsAtYesterday() {
        AggregationOutcome outcome = aggregator(3).catchUp();

        assertThat(outcome.days()).containsExactly(
                LocalDate.parse("2026-10-07"), LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-09"));
        assertThat(stored.latestDate()).contains(LocalDate.parse("2026-10-09"));
    }

    @Test
    void everyDayWritesOneRowPerSourceInASingleUpsert() {
        reads.add(run(PipelineSource.FCTT, RunStatus.FAILED, at("2026-10-09T10:00:00Z")));

        aggregator(1).catchUp();

        assertThat(stored.upsertCalls()).isEqualTo(1);
        assertThat(stored.all()).extracting(DailyStats::source)
                .containsExactlyInAnyOrder(PipelineSource.values());
        DailyStats fctt = stored.all().stream().filter(row -> row.source() == PipelineSource.FCTT).findFirst()
                .orElseThrow();
        assertThat(fctt.runs()).isEqualTo(1);
        assertThat(fctt.failures()).isEqualTo(1);
        assertThat(stored.all()).filteredOn(row -> row.source() != PipelineSource.FCTT)
                .allSatisfy(row -> assertThat(row.runs()).isZero());
    }

    @Test
    void catchUpAfterAGapStartsTheDayAfterTheLatestStoredDay() {
        aggregator(31).aggregate(LocalDate.parse("2026-10-05"));
        stored.all();

        AggregationOutcome outcome = aggregator(31).catchUp();

        assertThat(outcome.days()).first().isEqualTo(LocalDate.parse("2026-10-06"));
        assertThat(outcome.days()).last().isEqualTo(LocalDate.parse("2026-10-09"));
        assertThat(outcome.days()).hasSize(4);
    }

    @Test
    void aGapLongerThanTheBackfillWindowOnlyBackfillsTheWindow() {
        aggregator(31).aggregate(LocalDate.parse("2026-08-01"));

        AggregationOutcome outcome = aggregator(2).catchUp();

        assertThat(outcome.days()).containsExactly(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-09"));
    }

    @Test
    void storedDaysAreNotRecomputed() {
        aggregator(2).catchUp();
        int calls = stored.upsertCalls();
        // new facts for an already stored day change nothing, and a second catch-up has nothing to do
        reads.add(run(PipelineSource.FCTT, RunStatus.FAILED, at("2026-10-09T10:00:00Z")));

        AggregationOutcome again = aggregator(2).catchUp();

        assertThat(again.isEmpty()).isTrue();
        assertThat(stored.upsertCalls()).isEqualTo(calls);
        assertThat(stored.all()).allSatisfy(row -> assertThat(row.runs()).isZero());
    }

    @Test
    void todayIsNeverAggregated() {
        assertThatThrownBy(() -> aggregator(31).aggregate(LocalDate.parse("2026-10-10")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> aggregator(31).aggregate(LocalDate.parse("2026-10-11")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stored.upsertCalls()).isZero();
    }

    @Test
    void aZeroBackfillWindowAggregatesNothingOnTheFirstRun() {
        assertThat(aggregator(0).catchUp().isEmpty()).isTrue();
        assertThat(stored.upsertCalls()).isZero();
    }

    @Test
    void todayFollowsTheZoneNotUtc() {
        // 23:30Z on the 10th is already the 11th in Madrid, so the 10th is complete
        clock.advance(java.time.Duration.ofHours(15).plusMinutes(30));
        assertThat(clock.now()).isEqualTo(at("2026-10-10T23:30:00Z"));

        AggregationOutcome outcome = aggregator(1).catchUp();

        assertThat(outcome.days()).containsExactly(LocalDate.parse("2026-10-10"));
    }

    @Test
    void repositoryFailuresPropagate() {
        InMemoryDailyStatsRepository failing = new InMemoryDailyStatsRepository() {
            @Override
            public synchronized void upsert(List<DailyStats> stats) {
                throw new IllegalStateException("database down");
            }
        };
        DailyStatsAggregator aggregator = new DailyStatsAggregator(reads, failing, clock, SETTINGS);

        assertThatThrownBy(aggregator::catchUp).isInstanceOf(IllegalStateException.class)
                .hasMessage("database down");
        assertThat(Set.copyOf(failing.all())).isEmpty();
    }
}
