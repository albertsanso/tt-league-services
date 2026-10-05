package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;

public record IngestRunState(
        String ingestRunId,
        String status,
        String outcome,
        boolean retryable,
        boolean packageAvailable,
        String error,
        IngestHealth health) {

    /** Ingest state without health data (an older ingest service, or a run still in progress). */
    public IngestRunState(
            String ingestRunId, String status, String outcome, boolean retryable, boolean packageAvailable,
            String error) {
        this(ingestRunId, status, outcome, retryable, packageAvailable, error, null);
    }


    public boolean finished() {
        return outcome != null;
    }
}
