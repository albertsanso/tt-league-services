package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayEvent;

/** Timeline entry; system changes carry the actor {@code system:tracker}. */
public record MatchDayEventDto(
        UUID id, UUID matchId, String kind, String actor, Instant occurredAt, UUID runId, String note) {

    static MatchDayEventDto from(MatchDayEvent event) {
        return new MatchDayEventDto(event.id(), event.matchId(), event.kind().name(), event.actor(),
                event.occurredAt(), event.runId(), event.note());
    }
}
