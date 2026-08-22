package org.cttelsamicsterrassa.data.core.domain.auth.user.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class UserCreatedEvent extends DomainEvent {
    private final UUID userId;
    private final String username;
    private final String email;

    public UserCreatedEvent(UUID userId, String username, String email) {
        super(ZonedDateTime.now(), userId.toString());
        this.userId = userId;
        this.username = username;
        this.email = email;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }
}
