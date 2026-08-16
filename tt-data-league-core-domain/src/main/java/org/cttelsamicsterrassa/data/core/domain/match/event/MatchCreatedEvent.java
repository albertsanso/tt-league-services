package org.cttelsamicsterrassa.data.core.domain.match.event;

import org.albertsanso.commons.event.DomainEvent;

import java.util.UUID;

public class MatchCreatedEvent extends DomainEvent {

    private final UUID matchId;

    private MatchCreatedEvent(UUID matchId) {
        super(java.time.ZonedDateTime.now(), matchId.toString());
        this.matchId = matchId;
    }

    public static MatchCreatedEvent of(UUID matchId) {
        return new MatchCreatedEvent(matchId);
    }

    public UUID getMatchId() {
        return matchId;
    }
}
