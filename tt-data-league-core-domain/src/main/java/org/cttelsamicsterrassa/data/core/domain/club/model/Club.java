package org.cttelsamicsterrassa.data.core.domain.club.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

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
    private final String name;

    private Club(UUID id, ImportSource source, String name) {
        this.id = id;
        this.source = source;
        this.name = name;
    }

    public static Club createNew(ImportSource source, String name) {
        return of(UUID.randomUUID(), source, name);
    }

    public static Club of(UUID id, ImportSource source, String name) {
        return new Club(id, source, name);
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
