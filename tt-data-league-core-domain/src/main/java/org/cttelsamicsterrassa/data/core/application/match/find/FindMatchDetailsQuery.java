package org.cttelsamicsterrassa.data.core.application.match.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindMatchDetailsQuery extends DomainQuery {
    private final UUID matchId;

    public FindMatchDetailsQuery(UUID matchId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.matchId = matchId;
    }

    public UUID getMatchId() {
        return matchId;
    }
}
