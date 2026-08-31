package org.cttelsamicsterrassa.data.api.rest.settings;

import org.cttelsamicsterrassa.data.core.domain.settings.model.SystemSetting;

import java.util.List;

public record SettingDto(String key, String category, String type, Object value, Object defaultValue,
                         long version, String label, String description, List<String> allowedValues,
                         Integer minimum, Integer maximum) {
    public static SettingDto from(SystemSetting setting) {
        return new SettingDto(setting.getKey(), setting.getCategory().name(), setting.getType().name(), setting.getValue(),
                setting.getDefaultValue(), setting.getVersion(), setting.getLabel(), setting.getDescription(),
                setting.getAllowedValues(), setting.getMinimum(), setting.getMaximum());
    }
}
