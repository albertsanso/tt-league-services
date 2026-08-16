package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;

import java.util.UUID;

public class PlayerSeasonNameModifiedEvent extends DomainEvent {

    private final UUID seasonPlayerId;
    private final String name;

    private PlayerSeasonNameModifiedEvent(UUID seasonPlayerId, String name) {
        super(java.time.ZonedDateTime.now(), seasonPlayerId.toString());
        this.seasonPlayerId = seasonPlayerId;
        this.name = name;
    }

    public static PlayerSeasonNameModifiedEvent of(UUID seasonPlayerId, String name) {
        return new PlayerSeasonNameModifiedEvent(seasonPlayerId, name);
    }

    public UUID getSeasonPlayerId() {
        return seasonPlayerId;
    }

    public String getName() {
        return name;
    }
}
