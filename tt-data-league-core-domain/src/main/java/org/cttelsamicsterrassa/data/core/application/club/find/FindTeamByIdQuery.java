package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindTeamByIdQuery extends DomainQuery {

    private final UUID teamId;

    public FindTeamByIdQuery(UUID teamId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.teamId = teamId;
    }

    public UUID getTeamId() {
        return teamId;
    }
}
