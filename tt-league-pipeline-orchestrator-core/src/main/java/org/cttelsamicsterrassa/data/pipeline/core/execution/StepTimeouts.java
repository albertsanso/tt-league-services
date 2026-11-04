package org.cttelsamicsterrassa.data.pipeline.core.execution;

import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import java.time.Duration;

public record StepTimeouts(Duration ingest, Duration fetchPackage, Duration importJob) {

    public StepTimeouts {
        positive(ingest, "ingest");
        positive(fetchPackage, "fetchPackage");
        positive(importJob, "importJob");
    }

    public Duration of(StepKind kind) {
        return switch (kind) {
            case INGEST -> ingest;
            case FETCH_PACKAGE -> fetchPackage;
            case IMPORT -> importJob;
        };
    }

    static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
        return value;
    }
}
