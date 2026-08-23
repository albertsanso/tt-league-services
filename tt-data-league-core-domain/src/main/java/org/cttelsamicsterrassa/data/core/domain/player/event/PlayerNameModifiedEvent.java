package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PlayerNameModifiedEvent extends DomainEvent {
    private final UUID playerId;
    private final String newPlayerName;

    private PlayerNameModifiedEvent(UUID playerId, String newPlayerName) {
        super(ZonedDateTime.now(), playerId.toString());
        this.playerId = playerId;
        this.newPlayerName = newPlayerName;
    }

    public static PlayerNameModifiedEvent of(UUID playerId, String newPlayerName) {
        return new PlayerNameModifiedEvent(playerId, newPlayerName);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getNewPlayerName() {
        return newPlayerName;
    }
}
