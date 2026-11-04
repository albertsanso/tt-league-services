package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.time.Instant;
import java.util.UUID;

/** Entry of a match day's append-only timeline. System changes use actor {@link #SYSTEM_ACTOR}. */
public record MatchDayEvent(
        UUID id,
        UUID matchDayId,
        UUID matchId,
        MatchDayEventKind kind,
        String actor,
        Instant occurredAt,
        UUID runId,
        String note) {

    public static final String SYSTEM_ACTOR = "system:tracker";

    public MatchDayEvent {
        TrackerChecks.required(id, "id");
        TrackerChecks.required(matchDayId, "matchDayId");
        TrackerChecks.required(kind, "kind");
        TrackerChecks.nonBlankMax(actor, "actor", 128);
        TrackerChecks.required(occurredAt, "occurredAt");
        if (kind == MatchDayEventKind.NOTE) {
            TrackerChecks.nonBlankMax(note, "note", 2000);
        } else {
            TrackerChecks.optionalMax(note, "note", 2000);
        }
    }

    public static MatchDayEvent of(
            UUID matchDayId, UUID matchId, MatchDayEventKind kind, String actor, Instant occurredAt, UUID runId,
            String note) {
        return new MatchDayEvent(UUID.randomUUID(), matchDayId, matchId, kind, actor, occurredAt, runId, note);
    }
}
