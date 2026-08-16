package org.cttelsamicsterrassa.data.core.domain.match.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class MatchDeletedEvent extends DomainEvent {
    private final UUID matchId;

    private MatchDeletedEvent(UUID matchId) {
        super(ZonedDateTime.now(), matchId.toString());
        this.matchId = matchId;
    }

    public static MatchDeletedEvent of(UUID matchId) {
        return new MatchDeletedEvent(matchId);
    }

    public UUID getMatchId() {
        return matchId;
    }
}
