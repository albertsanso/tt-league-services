package org.cttelsamicsterrassa.data.core.domain.resource.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ResourceDeletedEvent extends DomainEvent {
    private final UUID resourceId;

    private ResourceDeletedEvent(UUID resourceId) {
        super(ZonedDateTime.now(), resourceId.toString());
        this.resourceId = resourceId;
    }

    public static ResourceDeletedEvent of(UUID resourceId) {
        return new ResourceDeletedEvent(resourceId);
    }

    public UUID getResourceId() {
        return resourceId;
    }
}
