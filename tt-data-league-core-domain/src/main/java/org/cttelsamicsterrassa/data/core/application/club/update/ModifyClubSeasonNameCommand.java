package org.cttelsamicsterrassa.data.core.application.club.update;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ModifyClubSeasonNameCommand extends DomainCommand {

    private final UUID clubSeasonId;
    private final String name;

    public ModifyClubSeasonNameCommand(ZonedDateTime occurredOn, String uuid, UUID clubSeasonId, String name) {
        super(occurredOn, uuid);
        this.clubSeasonId = clubSeasonId;
        this.name = name;
    }

    public UUID getClubSeasonId() {
        return clubSeasonId;
    }

    public String getName() {
        return name;
    }
}
