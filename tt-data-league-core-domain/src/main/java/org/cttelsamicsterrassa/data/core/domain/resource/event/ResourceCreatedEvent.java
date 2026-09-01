package org.cttelsamicsterrassa.data.core.domain.resource.event;

import org.albertsanso.commons.event.DomainEvent;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ResourceCreatedEvent extends DomainEvent {
    private final UUID resourceId;
    private final ResourceType resourceType;
    private final String name;
    private final String logicPath;

    private ResourceCreatedEvent(UUID resourceId, ResourceType resourceType, String name, String logicPath) {
        super(ZonedDateTime.now(), resourceId.toString());
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        this.name = name;
        this.logicPath = logicPath;
    }

    public static ResourceCreatedEvent of(UUID resourceId, ResourceType resourceType, String name, String logicPath) {
        return new ResourceCreatedEvent(resourceId, resourceType, name, logicPath);
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getName() {
        return name;
    }

    public String getLogicPath() {
        return logicPath;
    }
}
