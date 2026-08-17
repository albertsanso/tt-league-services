package org.cttelsamicsterrassa.data.core.application.club.delete;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class DeleteClubSeasonCommand extends DomainCommand {

    private final UUID clubSeasonId;

    public DeleteClubSeasonCommand(UUID clubSeasonId) {
        super(ZonedDateTime.now(), clubSeasonId.toString());
        this.clubSeasonId = clubSeasonId;
    }

    public UUID getClubSeasonId() {
        return clubSeasonId;
    }
}
