package org.cttelsamicsterrassa.data.pipeline.core.trigger.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

public class PendingTriggerExistsException extends RuntimeException {

    private final PipelineSource source;

    public PendingTriggerExistsException(PipelineSource source) {
        super("Source " + source + " already has a pending trigger");
        this.source = source;
    }

    public PendingTriggerExistsException(PipelineSource source, Throwable cause) {
        super("Source " + source + " already has a pending trigger", cause);
        this.source = source;
    }

    public PipelineSource source() {
        return source;
    }
}
