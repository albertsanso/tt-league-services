package org.cttelsamicsterrassa.data.pipeline.core.statistics.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DailyStats;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DateRange;

/** Persistence of the daily snapshots. {@code DailyStatsAggregator} is the only writer. */
public interface DailyStatsRepository {

    /** Inserts or replaces the rows, keyed by date and source. */
    void upsert(List<DailyStats> stats);

    /** Rows of the range ordered by date, then source; an empty source set means all sources. */
    List<DailyStats> find(DateRange range, Set<PipelineSource> sources);

    Optional<LocalDate> latestDate();
}
