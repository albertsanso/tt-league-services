package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FederatedPlayerCreatedEvent extends DomainEvent {

    private final UUID federatedPlayerId;
    private final String federatedPlayerName;

    private FederatedPlayerCreatedEvent(UUID federatedPlayerId, String federatedPlayerName) {
        super(ZonedDateTime.now(), federatedPlayerId.toString());
        this.federatedPlayerId = federatedPlayerId;
        this.federatedPlayerName = federatedPlayerName;
    }

    public static FederatedPlayerCreatedEvent of(UUID federatedPlayerId, String federatedPlayerName) {
        return new FederatedPlayerCreatedEvent(federatedPlayerId, federatedPlayerName);
    }

    public UUID getFederatedPlayerId() {
        return federatedPlayerId;
    }

    public String getFederatedPlayerName() {
        return federatedPlayerName;
    }
}
