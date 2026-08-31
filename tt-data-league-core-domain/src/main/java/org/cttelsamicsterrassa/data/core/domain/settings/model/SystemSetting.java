package org.cttelsamicsterrassa.data.core.domain.settings.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.settings.event.SystemSettingCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.settings.event.SystemSettingValueChangedEvent;

import java.util.List;
import java.util.Objects;

public final class SystemSetting extends Entity {
    private final String key;
    private final SettingCategory category;
    private final SettingType type;
    private Object value;
    private final Object defaultValue;
    private long version;
    private final String label;
    private final String description;
    private final List<String> allowedValues;
    private final Integer minimum;
    private final Integer maximum;

    private SystemSetting(String key, SettingCategory category, SettingType type, Object value, Object defaultValue,
                          long version, String label, String description, List<String> allowedValues,
                          Integer minimum, Integer maximum) {
        this.key = Objects.requireNonNull(key);
        this.category = Objects.requireNonNull(category);
        this.type = Objects.requireNonNull(type);
        this.value = value;
        this.defaultValue = defaultValue;
        this.version = version;
        this.label = Objects.requireNonNull(label);
        this.description = Objects.requireNonNull(description);
        this.allowedValues = List.copyOf(allowedValues);
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static SystemSetting createNew(String key, SettingCategory category, SettingType type, Object value,
                                          Object defaultValue, String label, String description,
                                          List<String> allowedValues, Integer minimum, Integer maximum) {
        SystemSetting setting = new SystemSetting(key, category, type, value, defaultValue, 0, label, description,
                allowedValues, minimum, maximum);
        setting.publishEvent(SystemSettingCreatedEvent.of(key, value, 0));
        return setting;
    }

    public static SystemSetting createExisting(String key, SettingCategory category, SettingType type, Object value,
                                               Object defaultValue, long version, String label, String description,
                                               List<String> allowedValues, Integer minimum, Integer maximum) {
        return new SystemSetting(key, category, type, value, defaultValue, version, label, description,
                allowedValues, minimum, maximum);
    }

    public void changeValue(Object newValue) {
        if (!Objects.equals(value, newValue)) {
            value = newValue;
            version++;
            publishEvent(SystemSettingValueChangedEvent.of(key, value, version));
        }
    }

    public String getKey() { return key; }
    public SettingCategory getCategory() { return category; }
    public SettingType getType() { return type; }
    public Object getValue() { return value; }
    public Object getDefaultValue() { return defaultValue; }
    public long getVersion() { return version; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public List<String> getAllowedValues() { return allowedValues; }
    public Integer getMinimum() { return minimum; }
    public Integer getMaximum() { return maximum; }
}
