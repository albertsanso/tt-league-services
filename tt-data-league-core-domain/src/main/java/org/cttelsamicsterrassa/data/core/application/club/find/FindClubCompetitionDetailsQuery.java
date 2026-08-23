package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindClubCompetitionDetailsQuery extends DomainQuery {

    private final UUID clubId;
    private final Season season;
    private final String competition;

    public FindClubCompetitionDetailsQuery(UUID clubId, Season season, String competition) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.clubId = clubId;
        this.season = season;
        this.competition = competition;
    }

    public UUID getClubId() {
        return clubId;
    }

    public Season getSeason() {
        return season;
    }

    public String getCompetition() {
        return competition;
    }
}
