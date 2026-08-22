package org.cttelsamicsterrassa.data.core.domain.auth.model;

import org.albertsanso.commons.model.Entity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

public class User extends Entity {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

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

    public static User createNew(String username, String email, String plainPassword) {
        return new User(
                UUID.randomUUID(),
                ZonedDateTime.now(),
                username,
                email,
                PASSWORD_ENCODER.encode(plainPassword),
                true);
    }

    public static User createExisting(UUID id, ZonedDateTime createdAt, String username, String email, String passwordHash, boolean actived) {
        return new User(id, createdAt, username, email, passwordHash, actived);
    }

    public boolean verifyPassword(String plainPassword) {
        return PASSWORD_ENCODER.matches(plainPassword, this.passwordHash);
    }

    public void changePassword(String newPlainPassword) {
        passwordHash = PASSWORD_ENCODER.encode(newPlainPassword);
    }

    public void disable() {
        actived = false;
    }

    public void enable() {
        actived = true;
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
