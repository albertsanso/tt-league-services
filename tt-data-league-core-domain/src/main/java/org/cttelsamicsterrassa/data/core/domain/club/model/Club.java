package org.cttelsamicsterrassa.data.core.domain.club.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubNameModifiedEvent;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A table tennis club or team entity.
 *
 * <p>{@code source} records which federation supplied the row. Club names are source-scoped rather
 * than a shared namespace, so a lookup is always scoped to {@code (source, name)}, never to name
 * alone.</p>
 */
public class Club extends Entity {
    private final UUID id;
    private final ImportSource source;
    private String name;

    private Club(UUID id, ImportSource source, String name) {
        this.id = id;
        this.source = source;
        this.name = name;
    }

    public static Club createNew(ImportSource source, String name) {
        Club club = of(UUID.randomUUID(), source, name);
        club.publishClubCreatedEvent();
        return club;
    }

    public static Club createExisting(UUID id, ImportSource source, String name) {
        return of(id, source, name);
    }

    private static Club of(UUID id, ImportSource source, String name) {
        return new Club(id, source, name);
    }

    public void modifyName(String newName) {
        if (!this.name.equals(newName)) {
            this.name = newName;
            publishClubNameModifiedEvent();
        }
    }

    public void delete() {
        publishClubDeletedEvent();
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

    public UUID getId() {
        return id;
    }

    public ImportSource getSource() {
        return source;
    }

    public String getName() {
        return name;
    }

}
