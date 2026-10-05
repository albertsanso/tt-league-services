package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;

/**
 * Composes the operational readings the runtime exposes as gauges. It adds no rule: the pending figure is
 * {@link StatisticsQueries#pending}, so it always agrees with the statistics API.
 */
public final class OperationalGauges {

    private final StatisticsQueries queries;
    private final MatchDayRepository matchDays;

    public OperationalGauges(StatisticsQueries queries, MatchDayRepository matchDays) {
        this.queries = Objects.requireNonNull(queries, "queries is required");
        this.matchDays = Objects.requireNonNull(matchDays, "matchDays is required");
    }

    public OperationalReadings read() {
        PendingByAge pending = queries.pending(Set.of(), null);
        Map<PipelineSource, PendingByAge.SourcePending> bySource = new EnumMap<>(PipelineSource.class);
        pending.sources().forEach(row -> bySource.put(row.source(), row));
        Map<PipelineSource, Integer> open = new EnumMap<>(PipelineSource.class);
        for (PipelineSource source : PipelineSource.values()) {
            open.put(source, 0);
        }
        for (MatchDay day : matchDays.findByState(MatchDayState.OPEN)) {
            open.merge(day.key().source(), 1, Integer::sum);
        }
        return new OperationalReadings(pending.asOf(), bySource, open);
    }
}
