package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FederatedClubDeletedEvent extends DomainEvent {

    private final UUID federatedClubId;
    private final String federatedClubName;

    private FederatedClubDeletedEvent(UUID federatedClubId, String federatedClubName) {
        super(ZonedDateTime.now(), federatedClubId.toString());
        this.federatedClubId = federatedClubId;
        this.federatedClubName = federatedClubName;
    }

    public static FederatedClubDeletedEvent of(UUID federatedClubId, String federatedClubName) {
        return new FederatedClubDeletedEvent(federatedClubId, federatedClubName);
    }

    public UUID getFederatedClubId() {
        return federatedClubId;
    }

    public String getFederatedClubName() {
        return federatedClubName;
    }
}
