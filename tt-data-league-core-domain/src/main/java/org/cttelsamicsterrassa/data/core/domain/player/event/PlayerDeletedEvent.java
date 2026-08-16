package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;

public class PlayerDeletedEvent extends DomainEvent {

    private final String playerId;

    private PlayerDeletedEvent(String playerId) {
        super(java.time.ZonedDateTime.now(), playerId);
        this.playerId = playerId;
    }

    public static PlayerDeletedEvent of(String playerId) {
        return new PlayerDeletedEvent(playerId);
    }

    public String getPlayerId() {
        return playerId;
    }
}
