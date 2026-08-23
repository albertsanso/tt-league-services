package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PlayerDeletedEvent extends DomainEvent {
    private final UUID playerId;
    private final String playerName;

    private PlayerDeletedEvent(UUID playerId, String playerName) {
        super(ZonedDateTime.now(), playerId.toString());
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public static PlayerDeletedEvent of(UUID playerId, String playerName) {
        return new PlayerDeletedEvent(playerId, playerName);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }
}
