package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindClubSeasonByNameAndSeasonQuery extends DomainQuery {

    private final String clubSeasonName;
    private final Season season;

    public FindClubSeasonByNameAndSeasonQuery(String clubSeasonName, Season season) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.clubSeasonName = clubSeasonName;
        this.season = season;
    }

    public String getClubSeasonName() {
        return clubSeasonName;
    }

    public Season getSeason() {
        return season;
    }
}
