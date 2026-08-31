package org.cttelsamicsterrassa.data.core.domain.settings.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.settings.event.SettingCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.settings.event.SettingModifiedEvent;

import java.util.UUID;

public class Setting extends Entity {
    private final UUID id;
    private final SettingCategory settingCategory;
    private final String name;
    private String value;

    private Setting(UUID id, SettingCategory settingCategory, String name, String value) {
        this.id = id;
        this.settingCategory = settingCategory;
        this.name = name;
        this.value = value;
    }

    private static Setting of(UUID id, SettingCategory settingCategory, String name, String value) {
        return new Setting(id, settingCategory, name, value);
    }

    public static Setting createNew(SettingCategory settingCategory, String name, String value) {
        Setting setting = of(UUID.randomUUID(), settingCategory, name, value);
        setting.publishSettingCreatedEvent();
        return setting;
    }

    public static Setting createExisting(UUID id, SettingCategory settingCategory, String name, String value) {
        return of(id, settingCategory, name, value);
    }

    public void modifyValue(String newValue) {
        if (!this.value.equals(newValue)) {
            this.value = newValue;
            publishSettingModifiedEvent();
        }
    }

    private void publishSettingCreatedEvent() {
        publishEvent(SettingCreatedEvent.create(id, settingCategory, name, value));
    }

    private void publishSettingModifiedEvent() {
        publishEvent(SettingModifiedEvent.create(id));
    }

    public UUID getId() {
        return id;
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
