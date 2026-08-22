package org.cttelsamicsterrassa.data.core.domain.auth.user.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.auth.user.event.UserCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.auth.user.event.UserDisabledEvent;
import org.cttelsamicsterrassa.data.core.domain.auth.user.event.UserEnabledEvent;
import org.cttelsamicsterrassa.data.core.domain.auth.user.event.UserPasswordHashModifiedEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class User extends Entity {

    private final UUID id;
    private final ZonedDateTime createdAt;

    private String username;
    private String email;
    private String passwordHash;
    private boolean actived;

    private User(UUID id, ZonedDateTime createdAt, String username, String email, String passwordHash, boolean actived) {
        this.id = id;
        this.createdAt = createdAt;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.actived = actived;
    }

    private static User of(UUID id, ZonedDateTime createdAt, String username, String email, String passwordHash, boolean actived) {
        return new User(id, createdAt, username, email, passwordHash, actived);
    }

    public static User createNew(String username, String email, String passwordHash) {
        User user = new User(
                UUID.randomUUID(),
                ZonedDateTime.now(),
                username,
                email,
                passwordHash,
                true);
        user.publishUserCreatedEvent();
        return user;
    }

    public static User createExisting(UUID id, ZonedDateTime createdAt, String username, String email, String passwordHash, boolean actived) {
        return of(id, createdAt, username, email, passwordHash, actived);
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        publishUserPasswordHashChangedEvent();
    }

    public void disable() {
        actived = false;
        publishUserDisabledEvent();
    }

    public void enable() {
        actived = true;
        publishUserEnabledEvent();
    }

    private void publishUserCreatedEvent() {
        publishEvent(new UserCreatedEvent(this.id, this.username, this.email));
    }

    private void publishUserPasswordHashChangedEvent() {
        publishEvent(new UserPasswordHashModifiedEvent(this.id, this.passwordHash));
    }

    private void publishUserEnabledEvent() {
        publishEvent(new UserEnabledEvent(this.id));
    }

    private void publishUserDisabledEvent() {
        publishEvent(new UserDisabledEvent(this.id));
    }

    public String getUsername() {
        return username;
    }

    public boolean isActived() {
        return actived;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
