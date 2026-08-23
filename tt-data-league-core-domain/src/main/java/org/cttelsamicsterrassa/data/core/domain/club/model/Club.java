package org.cttelsamicsterrassa.data.core.domain.club.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubNameModifiedEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * The season-independent identity of a club.
 */
public class Club extends Entity {
    private final UUID id;
    private String name;

    private Club(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = validateName(name);
    }

    public static Club createNew(String name) {
        Club club = new Club(UUID.randomUUID(), name);
        club.publishClubCreatedEvent();
        return club;
    }

    public static Club createExisting(UUID id, String name) {
        return new Club(id, name);
    }

    public void modifyName(String newName) {
        String validatedName = validateName(newName);
        if (!this.name.equals(validatedName)) {
            this.name = validatedName;
            publishClubNameModifiedEvent();
        }
    }

    public void delete() {
        publishClubDeletedEvent();
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

    private void publishClubCreatedEvent() {
        publishEvent(ClubCreatedEvent.of(id, name));
    }

    private void publishClubNameModifiedEvent() {
        publishEvent(ClubNameModifiedEvent.of(id, name));
    }

    private void publishClubDeletedEvent() {
        publishEvent(ClubDeletedEvent.of(id, name));
    }
}
