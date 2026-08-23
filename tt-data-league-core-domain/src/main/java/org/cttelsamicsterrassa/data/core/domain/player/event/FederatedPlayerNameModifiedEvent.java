package org.cttelsamicsterrassa.data.core.domain.player.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;

public class FederatedPlayerNameModifiedEvent extends DomainEvent {

    private final String federatedPlayerId;
    private final String federatedPlayerName;

    private FederatedPlayerNameModifiedEvent(String federatedPlayerId, String federatedPlayerName) {
        super(ZonedDateTime.now(), federatedPlayerId);
        this.federatedPlayerId = federatedPlayerId;
        this.federatedPlayerName = federatedPlayerName;
    }

    public static FederatedPlayerNameModifiedEvent of(String federatedPlayerId, String federatedPlayerName) {
        return new FederatedPlayerNameModifiedEvent(federatedPlayerId, federatedPlayerName);
    }

    public String getFederatedPlayerId() {
        return federatedPlayerId;
    }

    public String getFederatedPlayerName() {
        return federatedPlayerName;
    }
}
