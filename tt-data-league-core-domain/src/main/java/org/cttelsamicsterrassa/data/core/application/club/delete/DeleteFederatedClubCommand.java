package org.cttelsamicsterrassa.data.core.application.club.delete;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class DeleteFederatedClubCommand extends DomainCommand {

    private final UUID federatedClubId;

    public DeleteFederatedClubCommand(UUID federatedClubId) {
        super(ZonedDateTime.now(), federatedClubId.toString());
        this.federatedClubId = federatedClubId;
    }

    public UUID getFederatedClubId() {
        return federatedClubId;
    }
}
