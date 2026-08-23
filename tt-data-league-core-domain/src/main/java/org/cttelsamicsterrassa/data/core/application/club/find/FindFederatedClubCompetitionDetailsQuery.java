package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindFederatedClubCompetitionDetailsQuery extends DomainQuery {

    private final UUID federatedClubId;
    private final Season season;
    private final String competition;

    public FindFederatedClubCompetitionDetailsQuery(UUID federatedClubId, Season season, String competition) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.federatedClubId = federatedClubId;
        this.season = season;
        this.competition = competition;
    }

    public UUID getFederatedClubId() {
        return federatedClubId;
    }

    public Season getSeason() {
        return season;
    }

    public String getCompetition() {
        return competition;
    }
}
