package org.cttelsamicsterrassa.data.pipeline.core.polling.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** A polling policy override was changed by another writer, or does not exist at the expected version. */
public class StalePollPolicyException extends RuntimeException {

    private final PipelineSource source;

    public StalePollPolicyException(PipelineSource source, long expectedVersion) {
        super("Polling policy of " + source + " is not at version " + expectedVersion);
        this.source = source;
    }

    public PipelineSource source() {
        return source;
    }
}
