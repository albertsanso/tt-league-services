package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindClubDetailsQuery extends DomainQuery {
    private final UUID clubId;

    public FindClubDetailsQuery(UUID clubId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.clubId = clubId;
    }

    public UUID getClubId() {
        return clubId;
    }
}
