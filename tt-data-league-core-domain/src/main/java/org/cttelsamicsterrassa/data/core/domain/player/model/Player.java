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

    private Player(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = validateName(name);
    }

    public static Player createNew(String name) {
        Player player = new Player(UUID.randomUUID(), name);
        player.publishPlayerCreatedEvent();
        return player;
    }

    public static Player createExisting(UUID id, String name) {
        return new Player(id, name);
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
