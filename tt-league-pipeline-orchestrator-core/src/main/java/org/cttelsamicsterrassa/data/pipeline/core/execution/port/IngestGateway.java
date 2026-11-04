package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

/** Port to the ingest service. Failures raise {@link GatewayException}; sink failures pass through unchanged. */
public interface IngestGateway {

    /** Starts a download, parse and package run and returns the ingest run id. */
    String startRun(IngestRunRequest request);

    IngestRunState getRun(String ingestRunId);

    /** Streams the package into the sink and returns the declared and stored checksums. */
    FetchedPackage fetchPackage(String ingestRunId, PackageSink sink);
}
