package org.cttelsamicsterrassa.data.core.domain.player.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.UUID;

public class Player extends Entity {
    private final UUID id;
    private final ImportSource source;
    private final String name;

    private Player(UUID id, ImportSource source, String name) {
        this.id = id;
        this.source = source;
        this.name = name;
    }

    public static Player createNew(ImportSource source, String name) {
        return of(UUID.randomUUID(), source, name);
    }

    public static Player of(UUID id, ImportSource source, String name) {
        return new Player(id, source, name);
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
