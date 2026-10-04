package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.OpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.ScopeUnavailableException;

/** Placeholder until the match-day tracker and scope builder exist; a real resolver bean replaces it. */
final class UnavailableOpenMatchDayScopeResolver implements OpenMatchDayScopeResolver {

    @Override
    public RunScope resolve(PipelineSource source, String season) {
        throw new ScopeUnavailableException(ScopeUnavailableException.SCOPE_UNAVAILABLE,
                "Open match days are not available until the match-day tracker and scope builder are deployed");
    }
}
