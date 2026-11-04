package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** A source and season pair, the scope of one recompute. */
public record SourceSeason(PipelineSource source, String season) {

    public SourceSeason {
        TrackerChecks.required(source, "source");
        PipelineRun.requireValidSeason(season);
    }
}
