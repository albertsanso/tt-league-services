package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindClubByNameQuery extends DomainQuery {

    private final String clubName;

    public FindClubByNameQuery(ZonedDateTime occurredOn, String clubName) {
        super(occurredOn, UUID.randomUUID().toString());
        this.clubName = clubName;
    }

    public String getClubName() {
        return clubName;
    }
}
