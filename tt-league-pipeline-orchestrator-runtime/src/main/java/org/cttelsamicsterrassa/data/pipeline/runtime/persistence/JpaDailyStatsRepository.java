package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DailyStats;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DateRange;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsSettings;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.DailyStatsRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
class JpaDailyStatsRepository implements DailyStatsRepository {

    private final DailyStatsJpaRepository rows;
    private final StatisticsSettings settings;

    JpaDailyStatsRepository(DailyStatsJpaRepository rows, StatisticsSettings settings) {
        this.rows = rows;
        this.settings = settings;
    }

    /** One transaction: every row of a day is written or none. An existing (date, source) row is replaced. */
    @Override
    public void upsert(List<DailyStats> stats) {
        rows.saveAll(stats.stream().map(this::toEntity).toList());
        rows.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyStats> find(DateRange range, Set<PipelineSource> sources) {
        List<DailyStatsEntity> found = sources.isEmpty()
                ? rows.findByStatDateBetween(range.from(), range.to())
                : rows.findByStatDateBetweenAndSourceIn(range.from(), range.to(), sources);
        return found.stream()
                .map(this::toDomain)
                .sorted(Comparator.comparing(DailyStats::date).thenComparing(DailyStats::source))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> latestDate() {
        return rows.findLatestDate();
    }

    private DailyStatsEntity toEntity(DailyStats stats) {
        DailyStatsEntity entity = new DailyStatsEntity(stats.date(), stats.source());
        entity.runs = stats.runs();
        entity.failures = stats.failures();
        entity.matchesReported = stats.matchesReported();
        entity.avgTimeToReportSeconds =
                stats.avgTimeToReport() == null ? null : stats.avgTimeToReport().toSeconds();
        entity.pendingEndOfDay = stats.pendingEndOfDay();
        entity.zone = settings.zone().getId();
        entity.computedAt = stats.computedAt();
        return entity;
    }

    private DailyStats toDomain(DailyStatsEntity entity) {
        return new DailyStats(
                entity.statDate,
                entity.source,
                entity.runs,
                entity.failures,
                entity.matchesReported,
                entity.avgTimeToReportSeconds == null ? null : Duration.ofSeconds(entity.avgTimeToReportSeconds),
                entity.pendingEndOfDay,
                entity.computedAt);
    }
}
