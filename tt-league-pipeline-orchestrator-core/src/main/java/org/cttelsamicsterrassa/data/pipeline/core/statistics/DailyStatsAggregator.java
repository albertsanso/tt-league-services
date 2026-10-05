package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.DailyStatsRepository;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.StatisticsReadRepository;

/**
 * The only writer of the daily snapshots. A day is aggregated once, after it is complete, and never recomputed:
 * {@code pendingEndOfDay} comes from the state at aggregation time, so a day aggregated late is an approximation.
 * Repository failures propagate to the caller.
 */
public final class DailyStatsAggregator {

    private final StatisticsReadRepository reads;
    private final DailyStatsRepository stored;
    private final RunClock clock;
    private final StatisticsSettings settings;

    public DailyStatsAggregator(
            StatisticsReadRepository reads, DailyStatsRepository stored, RunClock clock, StatisticsSettings settings) {
        this.reads = Objects.requireNonNull(reads, "reads is required");
        this.stored = Objects.requireNonNull(stored, "stored is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.settings = Objects.requireNonNull(settings, "settings is required");
    }

    /** Writes one row per source for the day, zero rows included, in a single upsert. */
    public void aggregate(LocalDate day) {
        Objects.requireNonNull(day, "day is required");
        ZoneId zone = settings.zone();
        if (!day.isBefore(today())) {
            throw new IllegalArgumentException("day " + day + " is not complete yet in " + zone);
        }
        var start = StatisticsRules.startOf(day, zone);
        var end = StatisticsRules.startOf(day.plusDays(1), zone);
        List<RunFacts> runs = reads.terminalRunsFinishedBetween(start, end, Set.of());
        List<MatchFacts> matches = reads.matchesForDay(start, end);
        var computedAt = clock.now();
        List<DailyStats> rows = new ArrayList<>();
        for (PipelineSource source : PipelineSource.values()) {
            rows.add(StatisticsRules.dailyStats(source, day, runs, matches, zone, computedAt));
        }
        stored.upsert(rows);
    }

    /**
     * Aggregates every day from {@code max(latestDate + 1, today - backfillDays)} through yesterday, oldest first.
     * A stored day is never recomputed.
     */
    public AggregationOutcome catchUp() {
        LocalDate today = today();
        LocalDate earliest = today.minusDays(settings.backfillDays());
        Optional<LocalDate> latest = stored.latestDate();
        LocalDate from = latest.map(date -> date.plusDays(1)).filter(date -> date.isAfter(earliest)).orElse(earliest);
        List<LocalDate> done = new ArrayList<>();
        for (LocalDate day = from; day.isBefore(today); day = day.plusDays(1)) {
            aggregate(day);
            done.add(day);
        }
        return new AggregationOutcome(done);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.now(), settings.zone());
    }
}
