package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.OpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.ScopeUnavailableException;

/** Returns a scripted scope or throws a scripted exception; records the sources it was asked about. */
public class StubOpenMatchDayScopeResolver implements OpenMatchDayScopeResolver {

    public final java.util.List<PipelineSource> resolved = new java.util.ArrayList<>();
    private RunScope scope;
    private ScopeUnavailableException failure;

    public void returning(RunScope scope) {
        this.scope = scope;
        this.failure = null;
    }

    public void failingWith(ScopeUnavailableException failure) {
        this.failure = failure;
    }

    @Override
    public RunScope resolve(PipelineSource source, String season) {
        resolved.add(source);
        if (failure != null) {
            throw failure;
        }
        return scope;
    }
}
