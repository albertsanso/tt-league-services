package org.cttelsamicsterrassa.data.pipeline.core.run.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

public class ActiveRunConflictException extends RuntimeException {

    private final PipelineSource source;

    public ActiveRunConflictException(PipelineSource source) {
        super("Source " + source + " already has an active run");
        this.source = source;
    }

    public ActiveRunConflictException(PipelineSource source, Throwable cause) {
        super("Source " + source + " already has an active run", cause);
        this.source = source;
    }

    public PipelineSource source() {
        return source;
    }
}
