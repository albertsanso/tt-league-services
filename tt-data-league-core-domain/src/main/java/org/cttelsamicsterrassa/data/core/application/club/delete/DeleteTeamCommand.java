package org.cttelsamicsterrassa.data.core.application.club.delete;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class DeleteTeamCommand extends DomainCommand {

    private final UUID teamId;

    public DeleteTeamCommand(UUID teamId) {
        super(ZonedDateTime.now(), teamId.toString());
        this.teamId = teamId;
    }

    public UUID getTeamId() {
        return teamId;
    }
}
