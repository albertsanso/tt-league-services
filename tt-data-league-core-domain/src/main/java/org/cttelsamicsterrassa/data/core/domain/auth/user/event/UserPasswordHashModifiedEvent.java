package org.cttelsamicsterrassa.data.core.domain.auth.user.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class UserPasswordHashModifiedEvent extends DomainEvent {
    private final UUID userId;
    private final String newPasswordHash;

    public UserPasswordHashModifiedEvent(UUID userId, String newPasswordHash) {
        super(ZonedDateTime.now(), userId.toString());
        this.userId = userId;
        this.newPasswordHash = newPasswordHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getNewPasswordHash() {
        return newPasswordHash;
    }
}
