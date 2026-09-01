package org.cttelsamicsterrassa.data.core.domain.load.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public class ImportResource extends Entity {
    private final UUID id;
    private final Resource resource;
    private final Optional<Boolean> valid;
    private final ResourceType type;
    private final ZonedDateTime created;
    private final Optional<ZonedDateTime> lastProcessedDate;
    private final Season season;
    private final ImportSource source;

    private ImportResource(UUID id, Resource resource, Optional<Boolean> valid, ResourceType type, ZonedDateTime created, Optional<ZonedDateTime> lastProcessedDate, Season season, ImportSource source) {
        this.id = id;
        this.resource = resource;
        this.valid = valid;
        this.type = type;
        this.created = created;
        this.lastProcessedDate = lastProcessedDate;
        this.season = season;
        this.source = source;
    }

    private static ImportResource of(UUID id, Resource resource, Optional<Boolean> valid, ResourceType type, ZonedDateTime created, Optional<ZonedDateTime> lastProcessedDate, Season season, ImportSource source) {
        return new ImportResource(id, resource, valid, type, created, lastProcessedDate, season, source);
    }

    public static ImportResource createNew(Resource resource, Optional<Boolean> valid, ResourceType type, ZonedDateTime created, Optional<ZonedDateTime> lastProcessedDate, Season season, ImportSource source) {
        return new ImportResource(UUID.randomUUID(), resource, valid, type, created, lastProcessedDate, season, source);
    }

    public static ImportResource createExisting(UUID id, Resource resource, Optional<Boolean> valid, ResourceType type, ZonedDateTime created, Optional<ZonedDateTime> lastProcessedDate, Season season, ImportSource source) {
        return new ImportResource(id, resource, valid, type, created, lastProcessedDate, season, source);
    }

    public UUID getId() {
        return id;
    }

    public Resource getResource() {
        return resource;
    }

    public Optional<Boolean> getValid() {
        return valid;
    }

    public ResourceType getType() {
        return type;
    }

    public ZonedDateTime getCreated() {
        return created;
    }

    public Optional<ZonedDateTime> getLastProcessedDate() {
        return lastProcessedDate;
    }

    public Season getSeason() {
        return season;
    }

    public ImportSource getSource() {
        return source;
    }
}
