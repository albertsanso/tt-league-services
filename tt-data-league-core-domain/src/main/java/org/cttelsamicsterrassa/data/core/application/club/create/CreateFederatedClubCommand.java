package org.cttelsamicsterrassa.data.core.application.club.create;

import org.albertsanso.commons.command.DomainCommand;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.UUID;

public class CreateFederatedClubCommand extends DomainCommand {
    private final UUID federatedClubId;
    private final String federatedClubName;
    private final ImportSource source;

    public CreateFederatedClubCommand(ZonedDateTime occurredOn, String uuid, UUID federatedClubId, String federatedClubName, ImportSource source) {
        super(occurredOn, uuid);
        this.federatedClubId = federatedClubId;
        this.federatedClubName = federatedClubName;
        this.source = source;
    }

    public UUID getFederatedClubId() {
        return federatedClubId;
    }

    public String getFederatedClubName() {
        return federatedClubName;
    }

    public ImportSource getSource() {
        return source;
    }
}
