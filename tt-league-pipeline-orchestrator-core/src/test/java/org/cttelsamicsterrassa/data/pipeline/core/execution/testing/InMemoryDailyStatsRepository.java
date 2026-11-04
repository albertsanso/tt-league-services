package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DailyStats;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DateRange;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.DailyStatsRepository;

/** Rows keyed by date and source; counts the upsert calls so a test can check the single write per day. */
public class InMemoryDailyStatsRepository implements DailyStatsRepository {

    private final Map<String, DailyStats> rows = new TreeMap<>();
    private int upsertCalls;

    @Override
    public synchronized void upsert(List<DailyStats> stats) {
        upsertCalls++;
        for (DailyStats row : stats) {
            rows.put(row.date() + "/" + row.source().ordinal(), row);
        }
    }

    @Override
    public synchronized List<DailyStats> find(DateRange range, Set<PipelineSource> sources) {
        List<DailyStats> found = new ArrayList<>();
        for (DailyStats row : rows.values()) {
            if (range.contains(row.date()) && (sources.isEmpty() || sources.contains(row.source()))) {
                found.add(row);
            }
        }
        found.sort(Comparator.comparing(DailyStats::date).thenComparing(DailyStats::source));
        return found;
    }

    @Override
    public synchronized Optional<LocalDate> latestDate() {
        return rows.values().stream().map(DailyStats::date).max(Comparator.naturalOrder());
    }

    public synchronized int upsertCalls() {
        return upsertCalls;
    }

    public synchronized List<DailyStats> all() {
        return List.copyOf(rows.values());
    }
}
