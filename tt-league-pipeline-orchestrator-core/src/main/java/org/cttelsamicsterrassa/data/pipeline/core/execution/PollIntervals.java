package org.cttelsamicsterrassa.data.pipeline.core.execution;

import java.time.Duration;

public record PollIntervals(Duration ingest, Duration importJob) {

    public PollIntervals {
        StepTimeouts.positive(ingest, "ingest");
        StepTimeouts.positive(importJob, "importJob");
    }
}
