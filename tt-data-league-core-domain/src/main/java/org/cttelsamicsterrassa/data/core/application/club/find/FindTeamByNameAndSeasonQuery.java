package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindTeamByNameAndSeasonQuery extends DomainQuery {

    private final String teamName;
    private final Season season;

    public FindTeamByNameAndSeasonQuery(String teamName, Season season) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.teamName = teamName;
        this.season = season;
    }

    public String getTeamName() {
        return teamName;
    }

    public Season getSeason() {
        return season;
    }
}
