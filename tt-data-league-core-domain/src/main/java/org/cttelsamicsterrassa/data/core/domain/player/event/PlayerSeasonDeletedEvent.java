package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;

import java.util.UUID;

public class PlayerSeasonDeletedEvent extends DomainEvent {

    private final UUID playerSeasonId;

    private PlayerSeasonDeletedEvent(UUID playerSeasonId) {
        super(java.time.ZonedDateTime.now(), playerSeasonId.toString());
        this.playerSeasonId = playerSeasonId;
    }

    public static PlayerSeasonDeletedEvent of(UUID playerSeasonId) {
        return new PlayerSeasonDeletedEvent(playerSeasonId);
    }

    public UUID getPlayerSeasonId() {
        return playerSeasonId;
    }
}
