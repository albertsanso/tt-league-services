package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindClubSeasonByIdQuery extends DomainQuery {

    private final UUID clubSeasonId;

    public FindClubSeasonByIdQuery(UUID clubSeasonId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.clubSeasonId = clubSeasonId;
    }

    public UUID getClubSeasonId() {
        return clubSeasonId;
    }
}
