package org.cttelsamicsterrassa.data.core.application.club.create;

import org.albertsanso.commons.command.DomainCommand;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

public class CreateClubSeasonCommand extends DomainCommand {
    private final String clubName;
    private final Season season;

    public CreateClubSeasonCommand(String clubName, Season season) {
        super(java.time.ZonedDateTime.now(), java.util.UUID.randomUUID().toString());
        this.clubName = clubName;
        this.season = season;
    }

    public String getClubName() {
        return clubName;
    }

    public Season getSeason() {
        return season;
    }
}
