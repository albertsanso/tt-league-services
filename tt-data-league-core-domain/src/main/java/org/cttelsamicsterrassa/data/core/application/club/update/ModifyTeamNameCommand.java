package org.cttelsamicsterrassa.data.core.application.club.update;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ModifyTeamNameCommand extends DomainCommand {

    private final UUID teamId;
    private final String name;

    public ModifyTeamNameCommand(ZonedDateTime occurredOn, String uuid, UUID teamId, String name) {
        super(occurredOn, uuid);
        this.teamId = teamId;
        this.name = name;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getName() {
        return name;
    }
}
