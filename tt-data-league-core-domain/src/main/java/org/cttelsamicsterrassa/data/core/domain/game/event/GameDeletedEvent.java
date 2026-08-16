package org.cttelsamicsterrassa.data.core.domain.game.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class GameDeletedEvent extends DomainEvent {

    private final UUID gameId;

    private GameDeletedEvent(UUID gameId) {
        super(ZonedDateTime.now(), gameId.toString());
        this.gameId = gameId;
    }

    public static GameDeletedEvent of(UUID gameId) {
        return new GameDeletedEvent(gameId);
    }

    public UUID getGameId() {
        return gameId;
    }
}
