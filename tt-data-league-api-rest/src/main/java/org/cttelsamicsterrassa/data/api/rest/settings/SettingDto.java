package org.cttelsamicsterrassa.data.api.rest.settings;

import org.cttelsamicsterrassa.data.core.domain.settings.model.SystemSetting;

import java.util.List;

public record SettingDto(String key, String category, String type, Object value, Object defaultValue,
                         long version, String label, String description, List<String> allowedValues,
                         Integer minimum, Integer maximum) {
    public static SettingDto from(SystemSetting setting) {
        return new SettingDto(setting.key(), setting.category().name(), setting.type().name(), setting.value(),
                setting.defaultValue(), setting.version(), setting.label(), setting.description(),
                setting.allowedValues(), setting.minimum(), setting.maximum());
    }
}
