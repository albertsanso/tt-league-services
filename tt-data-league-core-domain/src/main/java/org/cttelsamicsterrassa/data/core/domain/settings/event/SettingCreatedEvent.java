package org.cttelsamicsterrassa.data.core.domain.settings.event;

import org.albertsanso.commons.event.DomainEvent;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;

import java.time.ZonedDateTime;
import java.util.UUID;

public class SettingCreatedEvent extends DomainEvent {
    private final UUID id;
    private final UUID settingId;
    private final SettingCategory settingCategory;
    private final String name;
    private final String value;

    private SettingCreatedEvent(UUID id, UUID settingId, SettingCategory settingCategory, String name, String value) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.id = id;
        this.settingId = settingId;
        this.settingCategory = settingCategory;
        this.name = name;
        this.value = value;
    }

    public static SettingCreatedEvent create(UUID settingId, SettingCategory settingCategory, String name, String value) {
        return new SettingCreatedEvent(UUID.randomUUID(), settingId, settingCategory, name, value);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSettingId() {
        return settingId;
    }

    public SettingCategory getSettingCategory() {
        return settingCategory;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
