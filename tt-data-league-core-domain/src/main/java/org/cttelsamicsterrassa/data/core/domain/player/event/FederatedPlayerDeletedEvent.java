package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;

public class FederatedPlayerDeletedEvent extends DomainEvent {

    private final String federatedPlayerId;

    private FederatedPlayerDeletedEvent(String federatedPlayerId) {
        super(java.time.ZonedDateTime.now(), federatedPlayerId);
        this.federatedPlayerId = federatedPlayerId;
    }

    public static FederatedPlayerDeletedEvent of(String federatedPlayerId) {
        return new FederatedPlayerDeletedEvent(federatedPlayerId);
    }

    public String getFederatedPlayerId() {
        return federatedPlayerId;
    }
}
