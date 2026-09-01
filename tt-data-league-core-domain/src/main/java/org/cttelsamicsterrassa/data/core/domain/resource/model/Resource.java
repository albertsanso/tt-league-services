package org.cttelsamicsterrassa.data.core.domain.resource.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.resource.event.ResourceCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.resource.event.ResourceDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.resource.event.ResourceModifiedEvent;

import java.nio.file.Path;
import java.util.UUID;

public class Resource extends Entity {
    private final UUID id;
    private final ResourceType type;
    private final String name;
    private String logicPath;
    private final Path physicalPath;

    private Resource(UUID id, ResourceType type, String name, String logicPath, Path physicalPath) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.logicPath = logicPath;
        this.physicalPath = physicalPath;
    }

    private static Resource of(UUID id, ResourceType type, String name, String logicPath, Path physicalPath) {
        return new Resource(id, type, name, logicPath, physicalPath);
    }

    public static Resource createNew(ResourceType type, String name, String logicPath, Path physicalPath) {
        Resource resource = new Resource(UUID.randomUUID(), type, name, logicPath, physicalPath);
        resource.publishResourceCreatedEvent();
        return resource;
    }

    public static Resource createExisting(UUID id, ResourceType type, String name, String logicPath, Path physicalPath) {
        return new Resource(id, type, name, logicPath, physicalPath);
    }

    public void modifyLogicatPath(String newLogicalPath) {
        if (!this.logicPath.equals(newLogicalPath)) {
            this.logicPath = newLogicalPath;
            publishResourceModifiedEvent();
        }
    }

    public void delete() {
        publishResourceDeletedEvent();
    }

    private void publishResourceCreatedEvent() {
         publishEvent(ResourceCreatedEvent.of(id, type, name, logicPath));
    }

    private void publishResourceModifiedEvent() {
        publishEvent(ResourceModifiedEvent.of(id));
    }

    private void publishResourceDeletedEvent() {
        publishEvent(ResourceDeletedEvent.of(id));
    }

    public UUID getId() {
        return id;
    }

    public ResourceType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getLogicPath() {
        return logicPath;
    }

    public Path getPhysicalPath() {
        return physicalPath;
    }
}
