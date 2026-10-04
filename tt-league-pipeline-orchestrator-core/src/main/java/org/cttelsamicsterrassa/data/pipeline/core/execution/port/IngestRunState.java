package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

public record IngestRunState(
        String ingestRunId,
        String status,
        String outcome,
        boolean retryable,
        boolean packageAvailable,
        String error) {

    public boolean finished() {
        return outcome != null;
    }
}
