package org.cttelsamicsterrassa.data.api.rest.settings;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;

import java.util.UUID;

public record SettingDto(UUID id, String category, String name, String value) {
    public static SettingDto from(Setting setting) {
        return new SettingDto(
                setting.getId(),
                setting.getSettingCategory().name(),
                setting.getName(),
                setting.getValue());
    }
}
