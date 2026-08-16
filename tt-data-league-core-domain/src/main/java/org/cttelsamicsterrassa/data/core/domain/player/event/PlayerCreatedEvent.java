package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PlayerCreatedEvent extends DomainEvent {

    private final UUID playerId;
    private final String playerName;

    private PlayerCreatedEvent(UUID playerId, String playerName) {
        super(ZonedDateTime.now(), playerId.toString());
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public static PlayerCreatedEvent of(UUID playerId, String playerName) {
        return new PlayerCreatedEvent(playerId, playerName);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }
}
