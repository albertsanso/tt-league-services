package org.cttelsamicsterrassa.data.core.application.player.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindPlayerDetailsQuery extends DomainQuery {
    private final UUID playerId;

    public FindPlayerDetailsQuery(UUID playerId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }
}
