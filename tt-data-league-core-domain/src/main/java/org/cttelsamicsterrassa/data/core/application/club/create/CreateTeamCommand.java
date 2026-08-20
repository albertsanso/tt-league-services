package org.cttelsamicsterrassa.data.core.application.club.create;

import org.albertsanso.commons.command.DomainCommand;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

public class CreateTeamCommand extends DomainCommand {
    private final String clubName;
    private final Season season;
    private final ImportSource source;
    private final Club club;

    public CreateTeamCommand(String clubName, Season season, ImportSource source, Club club) {
        super(java.time.ZonedDateTime.now(), java.util.UUID.randomUUID().toString());
        this.clubName = clubName;
        this.season = season;
        this.source = source;
        this.club = club;
    }

    public String getClubName() {
        return clubName;
    }

    public Season getSeason() {
        return season;
    }

    public Club getClub() {
        return club;
    }

    public ImportSource getSource() {
        return source;
    }
}
