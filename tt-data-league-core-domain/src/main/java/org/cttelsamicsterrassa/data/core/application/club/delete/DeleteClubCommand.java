package org.cttelsamicsterrassa.data.core.application.club.delete;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class DeleteClubCommand extends DomainCommand {

    private final UUID clubId;

    public DeleteClubCommand(UUID clubId) {
        super(ZonedDateTime.now(), clubId.toString());
        this.clubId = clubId;
    }

    public UUID getClubId() {
        return clubId;
    }
}
