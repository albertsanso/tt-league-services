package org.cttelsamicsterrassa.data.core.domain.auth.user.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class UserDisabledEvent extends DomainEvent {
    private final UUID userId;

    public UserDisabledEvent(UUID userId) {
        super(ZonedDateTime.now(), userId.toString());
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
