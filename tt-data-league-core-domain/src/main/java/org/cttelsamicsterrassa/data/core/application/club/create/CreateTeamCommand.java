package org.cttelsamicsterrassa.data.core.application.club.create;

import org.albertsanso.commons.command.DomainCommand;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

public class CreateTeamCommand extends DomainCommand {
    private final String clubName;
    private final Season season;
    private final ImportSource source;
    private final FederatedClub federatedClub;

    public CreateTeamCommand(String clubName, Season season, ImportSource source, FederatedClub club) {
        super(java.time.ZonedDateTime.now(), java.util.UUID.randomUUID().toString());
        this.clubName = clubName;
        this.season = season;
        this.source = source;
        this.federatedClub = club;
    }

    public String getClubName() {
        return clubName;
    }

    public Season getSeason() {
        return season;
    }

    public FederatedClub getFederatedClub() {
        return federatedClub;
    }

    public ImportSource getSource() {
        return source;
    }
}
