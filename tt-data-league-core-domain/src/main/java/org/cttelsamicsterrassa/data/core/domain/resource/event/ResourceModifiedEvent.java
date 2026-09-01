package org.cttelsamicsterrassa.data.core.domain.resource.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ResourceModifiedEvent extends DomainEvent {
    private final UUID resourceId;

    private ResourceModifiedEvent(UUID resourceId) {
        super(ZonedDateTime.now(), resourceId.toString());
        this.resourceId = resourceId;
    }

    public static ResourceModifiedEvent of(UUID resourceId) {
        return new ResourceModifiedEvent(resourceId);
    }

    public UUID getResourceId() {
        return resourceId;
    }
}
