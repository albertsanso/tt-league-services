package org.cttelsamicsterrassa.data.pipeline.core.trigger.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;

/** Produces the scope of the match days that are still open for a source. */
public interface OpenMatchDayScopeResolver {

    /**
     * Returns a scope with at least one filter, or throws {@link ScopeUnavailableException} with code
     * {@code SCOPE_UNAVAILABLE} (no resolver deployed) or {@code NO_OPEN_MATCH_DAYS} (nothing to ingest).
     */
    RunScope resolve(PipelineSource source, String season);
}
