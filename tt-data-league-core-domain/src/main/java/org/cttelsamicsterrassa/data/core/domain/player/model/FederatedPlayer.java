package org.cttelsamicsterrassa.data.core.domain.player.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.player.event.FederatedPlayerCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.FederatedPlayerDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.FederatedPlayerNameModifiedEvent;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.UUID;

public class FederatedPlayer extends Entity {
    private final UUID id;
    private final ImportSource source;
    private String name;

    private FederatedPlayer(UUID id, ImportSource source, String name) {
        this.id = id;
        this.source = source;
        this.name = name;
    }

    public static FederatedPlayer createNew(ImportSource source, String name) {
        FederatedPlayer federatedPlayer = of(UUID.randomUUID(), source, name);
        federatedPlayer.publishFederatedPlayerCreatedEvent();
        return federatedPlayer;
    }

    public static FederatedPlayer createExisting(UUID id, ImportSource source, String name) {
        return of(id, source, name);
    }

    private static FederatedPlayer of(UUID id, ImportSource source, String name) {
        return new FederatedPlayer(id, source, name);
    }

    public void modifyName(String newName) {
        if (!this.name.equals(newName)) {
            this.name = newName;
            publishFederatedPlayerNameModifiedEvent();
        }
    }

    public void delete() {
        publishFederatedPlayerDeletedEvent();
    }

    private void publishFederatedPlayerCreatedEvent() {
        publishEvent(FederatedPlayerCreatedEvent.of(id, name));
    }

    private void publishFederatedPlayerNameModifiedEvent() {
        publishEvent(FederatedPlayerNameModifiedEvent.of(id.toString(), name));
    }

    private void publishFederatedPlayerDeletedEvent() {
        publishEvent(FederatedPlayerDeletedEvent.of(id.toString()));
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
