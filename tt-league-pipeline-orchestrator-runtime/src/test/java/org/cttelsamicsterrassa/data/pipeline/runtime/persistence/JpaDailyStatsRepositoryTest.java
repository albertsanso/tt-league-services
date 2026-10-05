package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DailyStats;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DateRange;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.DailyStatsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class JpaDailyStatsRepositoryTest extends AbstractPersistenceTest {

    private static final LocalDate DAY = LocalDate.parse("2026-10-03");

    @Autowired
    DailyStatsRepository stats;

    @Autowired
    JdbcTemplate template;

    private static DailyStats row(LocalDate date, PipelineSource source, int runs, Duration avg) {
        return new DailyStats(date, source, runs, runs > 0 ? 1 : 0, 2, avg, 3, T0);
    }

    @Test
    void roundTripsEveryFigureAndTheZone() {
        stats.upsert(List.of(row(DAY, PipelineSource.FCTT, 4, Duration.ofSeconds(5400))));

        DailyStats loaded = stats.find(new DateRange(DAY, DAY), Set.of()).get(0);

        assertThat(loaded).isEqualTo(new DailyStats(DAY, PipelineSource.FCTT, 4, 1, 2, Duration.ofSeconds(5400), 3, T0));
        assertThat(template.queryForObject("SELECT zone FROM pipeline.daily_stats", String.class))
                .isEqualTo("Europe/Madrid");
    }

    @Test
    void anAbsentAverageIsStoredAsNull() {
        stats.upsert(List.of(row(DAY, PipelineSource.RFETM, 0, null)));

        assertThat(stats.find(new DateRange(DAY, DAY), Set.of()).get(0).avgTimeToReport()).isNull();
        assertThat(template.queryForObject("SELECT avg_time_to_report_seconds FROM pipeline.daily_stats", Long.class))
                .isNull();
    }

    @Test
    void upsertReplacesTheRowOfTheSameDateAndSource() {
        stats.upsert(List.of(row(DAY, PipelineSource.FCTT, 1, Duration.ofHours(1))));

        stats.upsert(List.of(row(DAY, PipelineSource.FCTT, 5, Duration.ofHours(2))));

        List<DailyStats> loaded = stats.find(new DateRange(DAY, DAY), Set.of());
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).runs()).isEqualTo(5);
        assertThat(loaded.get(0).avgTimeToReport()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void findIsOrderedByDateThenSourceAndFiltersTheRangeAndSources() {
        stats.upsert(List.of(
                row(DAY.plusDays(1), PipelineSource.RFETM, 1, null),
                row(DAY, PipelineSource.FCTT, 1, null),
                row(DAY, PipelineSource.RFETM, 1, null),
                row(DAY.plusDays(1), PipelineSource.FCTT, 1, null),
                row(DAY.plusDays(5), PipelineSource.FCTT, 1, null)));

        List<DailyStats> all = stats.find(new DateRange(DAY, DAY.plusDays(1)), Set.of());

        assertThat(all).extracting(DailyStats::date, DailyStats::source).containsExactly(
                org.assertj.core.groups.Tuple.tuple(DAY, PipelineSource.RFETM),
                org.assertj.core.groups.Tuple.tuple(DAY, PipelineSource.FCTT),
                org.assertj.core.groups.Tuple.tuple(DAY.plusDays(1), PipelineSource.RFETM),
                org.assertj.core.groups.Tuple.tuple(DAY.plusDays(1), PipelineSource.FCTT));
        assertThat(stats.find(new DateRange(DAY, DAY.plusDays(5)), Set.of(PipelineSource.FCTT)))
                .extracting(DailyStats::date).containsExactly(DAY, DAY.plusDays(1), DAY.plusDays(5));
    }

    @Test
    void latestDateIsTheNewestStoredDay() {
        assertThat(stats.latestDate()).isEmpty();

        stats.upsert(List.of(row(DAY, PipelineSource.FCTT, 1, null), row(DAY.plusDays(2), PipelineSource.RFETM, 1, null)));

        assertThat(stats.latestDate()).contains(DAY.plusDays(2));
    }
}
