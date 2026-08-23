package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindFederatedClubDetailsQuery extends DomainQuery {

    private final UUID federatedClubId;

    public FindFederatedClubDetailsQuery(UUID federatedClubId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.federatedClubId = federatedClubId;
    }

    public UUID getFederatedClubId() {
        return federatedClubId;
    }
}
