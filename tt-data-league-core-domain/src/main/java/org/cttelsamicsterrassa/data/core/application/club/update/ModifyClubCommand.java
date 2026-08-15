package org.cttelsamicsterrassa.data.core.application.club.update;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;

public class ModifyClubCommand extends DomainCommand {
    protected ModifyClubCommand(ZonedDateTime occurredOn, String uuid) {
        super(occurredOn, uuid);
    }
}
