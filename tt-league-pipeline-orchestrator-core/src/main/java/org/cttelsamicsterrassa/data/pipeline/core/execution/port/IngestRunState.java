package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;

/**
 * @param progress the stage progress of a run still in progress; null when the ingest service reports none
 */
public record IngestRunState(
        String ingestRunId,
        String status,
        String outcome,
        boolean retryable,
        boolean packageAvailable,
        String error,
        IngestHealth health,
        IngestProgress progress) {

    /** Ingest state without progress. */
    public IngestRunState(
            String ingestRunId, String status, String outcome, boolean retryable, boolean packageAvailable,
            String error, IngestHealth health) {
        this(ingestRunId, status, outcome, retryable, packageAvailable, error, health, null);
    }

    /** Ingest state without health data (an older ingest service, or a run still in progress). */
    public IngestRunState(
            String ingestRunId, String status, String outcome, boolean retryable, boolean packageAvailable,
            String error) {
        this(ingestRunId, status, outcome, retryable, packageAvailable, error, null, null);
    }

    public boolean finished() {
        return outcome != null;
    }
}
