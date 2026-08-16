package org.cttelsamicsterrassa.data.core.domain.game.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class GameCreatedEvent extends DomainEvent {

    private final UUID gameId;

    private GameCreatedEvent(UUID gameId) {
        super(ZonedDateTime.now(), gameId.toString());
        this.gameId = gameId;
    }

    public static GameCreatedEvent of(UUID gameId) {
        return new GameCreatedEvent(gameId);
    }

    public UUID getGameId() {
        return gameId;
    }
}
