package org.cttelsamicsterrassa.data.core.domain.club.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.club.event.FederatedClubCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.club.event.FederatedClubDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.club.event.FederatedClubNameModifiedEvent;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A table tennis club or team entity.
 *
 * <p>{@code source} records which federation supplied the row. FederatedClub names are source-scoped rather
 * than a shared namespace, so a lookup is always scoped to {@code (source, name)}, never to name
 * alone.</p>
 */
public class FederatedClub extends Entity {
    private final UUID id;
    private final ImportSource source;
    private String name;

    private FederatedClub(UUID id, ImportSource source, String name) {
        this.id = id;
        this.source = source;
        this.name = name;
    }

    public static FederatedClub createNew(ImportSource source, String name) {
        FederatedClub club = of(UUID.randomUUID(), source, name);
        club.publishFederatedClubCreatedEvent();
        return club;
    }

    public static FederatedClub createExisting(UUID id, ImportSource source, String name) {
        return of(id, source, name);
    }

    private static FederatedClub of(UUID id, ImportSource source, String name) {
        return new FederatedClub(id, source, name);
    }

    public void modifyName(String newName) {
        if (!this.name.equals(newName)) {
            this.name = newName;
            publishFederatedClubNameModifiedEvent();
        }
    }

    public void delete() {
        publishFederatedClubDeletedEvent();
    }

    private void publishFederatedClubCreatedEvent() {
        publishEvent(FederatedClubCreatedEvent.of(id, name));
    }

    private void publishFederatedClubNameModifiedEvent() {
        publishEvent(FederatedClubNameModifiedEvent.of(id, name));
    }

    private void publishFederatedClubDeletedEvent() {
        publishEvent(FederatedClubDeletedEvent.of(id, name));
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
