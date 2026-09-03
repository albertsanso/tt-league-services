package org.cttelsamicsterrassa.data.core.domain.resource.event;

import org.albertsanso.commons.event.DomainEvent;
import java.time.ZonedDateTime;
import java.util.UUID;

public class ResourceCreatedEvent extends DomainEvent {
    private final UUID resourceId;
    private final String name;
    private final String logicPath;

    private ResourceCreatedEvent(UUID resourceId, String name, String logicPath) {
        super(ZonedDateTime.now(), resourceId.toString());
        this.resourceId = resourceId;
        this.name = name;
        this.logicPath = logicPath;
    }

    public static ResourceCreatedEvent of(UUID resourceId, String name, String logicPath) {
        return new ResourceCreatedEvent(resourceId, name, logicPath);
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getName() {
        return name;
    }

    public String getLogicPath() {
        return logicPath;
    }
}
