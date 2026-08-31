package org.cttelsamicsterrassa.data.core.domain.settings.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class SettingModifiedEvent extends DomainEvent {
    private final UUID id;

    private SettingModifiedEvent(UUID id) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.id = id;
    }

    public static SettingModifiedEvent create(UUID id) {
        return new SettingModifiedEvent(id);
    }

    public UUID getId() {
        return id;
    }
}
