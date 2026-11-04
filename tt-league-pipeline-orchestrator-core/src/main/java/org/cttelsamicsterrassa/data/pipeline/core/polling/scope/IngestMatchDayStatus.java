package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.util.List;
import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** The ingest match-day status of one source and season. */
public record IngestMatchDayStatus(PipelineSource source, String season, List<IngestStatusRow> rows) {

    public IngestMatchDayStatus {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(season, "season is required");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows is required"));
    }
}
