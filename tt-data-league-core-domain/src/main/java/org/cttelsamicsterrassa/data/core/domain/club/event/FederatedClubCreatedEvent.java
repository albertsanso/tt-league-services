package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FederatedClubCreatedEvent extends DomainEvent {

    private final UUID federatedClubId;
    private final String federatedClubName;

    private FederatedClubCreatedEvent(UUID federatedClubId, String federatedClubName) {
        super(ZonedDateTime.now(), federatedClubId.toString());
        this.federatedClubName = federatedClubName;
        this.federatedClubId = federatedClubId;
    }

    public static FederatedClubCreatedEvent of(UUID federatedClubId, String federatedClubName) {
        return new FederatedClubCreatedEvent(federatedClubId, federatedClubName);
    }

    public UUID getFederatedClubId() {
        return federatedClubId;
    }

    public String getFederatedClubName() {
        return federatedClubName;
    }
}
