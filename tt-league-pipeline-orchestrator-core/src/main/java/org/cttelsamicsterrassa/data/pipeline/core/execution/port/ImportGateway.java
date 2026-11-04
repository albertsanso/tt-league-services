package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.util.UUID;

/** Port to the platform import jobs API. Failures raise {@link GatewayException}. */
public interface ImportGateway {

    /** Submits the ZIP; the platform deduplicates by content, so a repeated submission is safe. */
    ImportSubmission submit(String fileName, ArtifactContent content, UUID clientRunId);

    ImportJobState getJob(UUID importJobId);
}
