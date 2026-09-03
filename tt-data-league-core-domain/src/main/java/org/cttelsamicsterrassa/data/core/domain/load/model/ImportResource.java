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
    private ImportResourceStatus status;

    private ImportResource(UUID id, Resource resource, Optional<Boolean> valid, ResourceType type, ZonedDateTime created, Optional<ZonedDateTime> lastProcessedDate, Season season, ImportSource source, ImportResourceStatus status) {
        this.id = id;
        this.resource = resource;
        this.valid = valid;
        this.type = type;
        this.created = created;
        this.lastProcessedDate = lastProcessedDate;
        this.season = season;
        this.source = source;
        this.status = status;
    }

    private static ImportResource of(UUID id, Resource resource, Optional<Boolean> valid, ResourceType type, ZonedDateTime created, Optional<ZonedDateTime> lastProcessedDate, Season season, ImportSource source, ImportResourceStatus status) {
        return new ImportResource(id, resource, valid, type, created, lastProcessedDate, season, source, status);
    }

    public static ImportResource createNew(Resource resource, Optional<Boolean> valid, ResourceType type, ZonedDateTime created, Optional<ZonedDateTime> lastProcessedDate, Season season, ImportSource source) {
        return of(UUID.randomUUID(), resource, valid, type, created, lastProcessedDate, season, source, ImportResourceStatus.PENDING);
    }

    public static ImportResource createExisting(UUID id, Resource resource, Optional<Boolean> valid, ResourceType type, ZonedDateTime created, Optional<ZonedDateTime> lastProcessedDate, Season season, ImportSource source, ImportResourceStatus status) {
        return of(id, resource, valid, type, created, lastProcessedDate, season, source, status);
    }

    public void setPending() {
        if (status == ImportResourceStatus.PENDING) return;

        if (ImportResourceStatus.getAllFinishedStatuses().contains(status)) {
            this.status = ImportResourceStatus.PENDING;
        } else {
            throw new IllegalStateException("Cannot set an import resource to PENDING status from its current status: " + status);
        }
    }

    public void startProcessing() {
        if (status != ImportResourceStatus.PENDING) {
            throw new IllegalStateException("Cannot start processing an import resource that is not in PENDING status.");
        }
        this.status = ImportResourceStatus.PROCESSING;
    }

    public void finishProcessing(boolean isValid) {
        if (status != ImportResourceStatus.PROCESSING) {
            throw new IllegalStateException("Cannot finish processing an import resource that is not in PROCESSING status.");
        }
        this.status = isValid ? ImportResourceStatus.PROCESSED : ImportResourceStatus.ERROR;
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

    public ImportResourceStatus getStatus() {
        return status;
    }
}
