package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindClubByIdQuery extends DomainQuery {

    private final UUID clubId;

    public FindClubByIdQuery(UUID clubId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.clubId = clubId;
    }

    public UUID getClubId() {
        return clubId;
    }
}
