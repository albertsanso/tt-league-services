package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;

public class PlayerNameModifiedEvent extends DomainEvent {

    private final String playerId;
    private final String newName;

    private PlayerNameModifiedEvent(String playerId, String newName) {
        super(ZonedDateTime.now(), playerId);
        this.playerId = playerId;
        this.newName = newName;
    }

    public static PlayerNameModifiedEvent of(String playerId, String newName) {
        return new PlayerNameModifiedEvent(playerId, newName);
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getName() {
        return newName;
    }
}
