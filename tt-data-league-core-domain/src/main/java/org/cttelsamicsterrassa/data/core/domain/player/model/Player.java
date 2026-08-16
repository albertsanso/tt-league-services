package org.cttelsamicsterrassa.data.core.domain.player.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.player.event.PlayerCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.PlayerDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.player.event.PlayerNameModifiedEvent;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.UUID;

public class Player extends Entity {
    private final UUID id;
    private final ImportSource source;
    private String name;

    private Player(UUID id, ImportSource source, String name) {
        this.id = id;
        this.source = source;
        this.name = name;
    }

    public static Player createNew(ImportSource source, String name) {
        Player player = of(UUID.randomUUID(), source, name);
        player.publishPlayerCreatedEvent();
        return player;
    }

    public static Player createExisting(UUID id, ImportSource source, String name) {
        return of(id, source, name);
    }

    private static Player of(UUID id, ImportSource source, String name) {
        return new Player(id, source, name);
    }

    public void modifyName(String newName) {
        if (!this.name.equals(newName)) {
            this.name = newName;
            publishPlayerNameModifiedEvent();
        }
    }

    public void delete() {
        publishPlayerDeletedEvent();
    }

    private void publishPlayerCreatedEvent() {
        publishEvent(PlayerCreatedEvent.of(id, name));
    }

    private void publishPlayerNameModifiedEvent() {
        publishEvent(PlayerNameModifiedEvent.of(id.toString(), name));
    }

    private void publishPlayerDeletedEvent() {
        publishEvent(PlayerDeletedEvent.of(id.toString()));
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
