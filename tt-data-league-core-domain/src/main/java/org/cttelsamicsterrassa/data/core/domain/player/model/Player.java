package org.cttelsamicsterrassa.data.core.domain.player.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.player.event.PlayerCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.PlayerDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.PlayerNameModifiedEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * The season-independent identity of a player.
 */
public class Player extends Entity {
    private final UUID id;
    private String name;
    private final String licenseId;

    private Player(UUID id, String name) {
        this(id, name, null);
    }

    private Player(UUID id, String name, String licenseId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = validateName(name);
        this.licenseId = licenseId;
    }

    public static Player createNew(String name) {
        return createNew(name, null);
    }

    public static Player createNew(String name, String licenseId) {
        Player player = new Player(UUID.randomUUID(), name, licenseId);
        player.publishPlayerCreatedEvent();
        return player;
    }

    public static Player createExisting(UUID id, String name) {
        return createExisting(id, name, null);
    }

    public static Player createExisting(UUID id, String name, String licenseId) {
        return new Player(id, name, licenseId);
    }

    public void modifyName(String newName) {
        String validatedName = validateName(newName);
        if (!this.name.equals(validatedName)) {
            this.name = validatedName;
            publishPlayerNameModifiedEvent();
        }
    }

    public void delete() {
        publishPlayerDeletedEvent();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLicenseId() {
        return licenseId;
    }

    public Player withLicenseId(String licenseId) {
        if (Objects.equals(this.licenseId, licenseId)) {
            return this;
        }
        return new Player(id, name, licenseId);
    }

    private static String validateName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }

    private void publishPlayerCreatedEvent() {
        publishEvent(PlayerCreatedEvent.of(id, name));
    }

    private void publishPlayerNameModifiedEvent() {
        publishEvent(PlayerNameModifiedEvent.of(id, name));
    }

    private void publishPlayerDeletedEvent() {
        publishEvent(PlayerDeletedEvent.of(id, name));
    }
}
