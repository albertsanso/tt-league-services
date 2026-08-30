package org.cttelsamicsterrassa.data.core.domain.settings.model;

import java.util.List;

public record SystemSetting(
        String key,
        SettingCategory category,
        SettingType type,
        Object value,
        Object defaultValue,
        long version,
        String label,
        String description,
        List<String> allowedValues,
        Integer minimum,
        Integer maximum) {
}
