package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.PendingTrigger;

public record PendingTriggerDto(
        String source,
        String season,
        String scopeType,
        List<ScopeFilterDto> filters,
        boolean force,
        String requestedBy,
        Instant requestedAt) {

    public static PendingTriggerDto from(PendingTrigger trigger) {
        return new PendingTriggerDto(trigger.source().name(), trigger.season(), trigger.scopeType().name(),
                trigger.filters().stream().map(ScopeFilterDto::from).toList(), trigger.force(),
                trigger.requestedBy(), trigger.requestedAt());
    }
}
