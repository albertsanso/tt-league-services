package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Duration;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Time to report per source and competition; {@code competition} is null on the source total row. */
public record TimeToReportStats(List<Row> rows) {

    public TimeToReportStats {
        rows = List.copyOf(rows);
    }

    public record Row(PipelineSource source, String competition, int count, Duration median, Duration p90) {}
}
