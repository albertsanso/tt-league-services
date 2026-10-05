package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Instant;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Matches still waiting for a result, per source, by age bucket; {@code overdue} counts those with status OVERDUE. */
public record PendingByAge(Instant asOf, List<SourcePending> sources) {

    public PendingByAge {
        sources = List.copyOf(sources);
    }

    public record SourcePending(
            PipelineSource source, int under1Day, int days1To2, int days2To7, int over7Days, int overdue) {}
}
