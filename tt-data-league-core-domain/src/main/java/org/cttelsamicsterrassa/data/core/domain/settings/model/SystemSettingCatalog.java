package org.cttelsamicsterrassa.data.core.domain.settings.model;

import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingNotFoundException;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingValidationException;

import java.util.List;

public final class SystemSettingCatalog {
    private record Definition(String key, SettingCategory category, SettingType type, Object defaultValue,
                              String label, String description, List<String> allowed, Integer min, Integer max) {}

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("ui.theme", SettingCategory.UI, SettingType.STRING, "light", "Theme", "Default application theme", List.of("light", "dark", "system"), null, null),
            new Definition("ui.compactMode", SettingCategory.UI, SettingType.BOOLEAN, false, "Compact mode", "Use compact spacing in the application", List.of(), null, null),
            new Definition("notifications.emailEnabled", SettingCategory.NOTIFICATIONS, SettingType.BOOLEAN, true, "Email notifications", "Enable application email notifications", List.of(), null, null),
            new Definition("notifications.inAppEnabled", SettingCategory.NOTIFICATIONS, SettingType.BOOLEAN, true, "In-app notifications", "Enable in-app notifications", List.of(), null, null),
            new Definition("notifications.importCompleted", SettingCategory.NOTIFICATIONS, SettingType.BOOLEAN, true, "Import completion notifications", "Notify administrators when an import completes", List.of(), null, null),
            new Definition("import.autoValidate", SettingCategory.IMPORT, SettingType.BOOLEAN, true, "Validate imports automatically", "Validate imported data before processing", List.of(), null, null),
            new Definition("import.preserveHistory", SettingCategory.IMPORT, SettingType.BOOLEAN, true, "Preserve import history", "Keep existing season and match history during imports", List.of(), null, null),
            new Definition("import.maxBatchSize", SettingCategory.IMPORT, SettingType.INTEGER, 1000, "Maximum import batch", "Maximum records accepted in one import batch", List.of(), 1, 10000),
            new Definition("display.maxSearchResults", SettingCategory.DISPLAY, SettingType.INTEGER, 50, "Maximum search results", "Maximum results displayed by searches", List.of(), 10, 100),
            new Definition("display.maxPageSize", SettingCategory.DISPLAY, SettingType.INTEGER, 50, "Maximum page size", "Maximum records displayed on one page", List.of(), 10, 100));

    public List<SystemSetting> defaults() {
        return DEFINITIONS.stream().map(d -> create(d, d.defaultValue, 0)).toList();
    }
    public List<SystemSetting> definitions() { return defaults(); }
    public SystemSetting rehydrate(String key, SettingType type, String encoded, long version) {
        Definition d = definition(key);
        if (d.type != type) throw new SettingValidationException("Setting type does not match catalog: " + key);
        return create(d, decode(type, encoded), version);
    }
    public Object validate(String key, Object value) {
        Definition d = definition(key);
        if (value == null) throw new SettingValidationException("Value is required");
        if (d.type == SettingType.BOOLEAN && !(value instanceof Boolean)) throw new SettingValidationException("Expected a boolean");
        if (d.type == SettingType.STRING && !(value instanceof String)) throw new SettingValidationException("Expected a string");
        if (d.type == SettingType.INTEGER && (!(value instanceof Number) || value instanceof Double || value instanceof Float
                || ((Number) value).longValue() != ((Number) value).doubleValue())) throw new SettingValidationException("Expected an integer");
        if (d.type == SettingType.INTEGER && ((d.min != null && ((Number)value).intValue() < d.min) || (d.max != null && ((Number)value).intValue() > d.max)))
            throw new SettingValidationException("Value must be between " + d.min + " and " + d.max);
        if (!d.allowed.isEmpty() && !d.allowed.contains(value)) throw new SettingValidationException("Value is not supported");
        return value;
    }
    public String encode(String key, Object value) { return String.valueOf(validate(key, value)); }
    private SystemSetting create(Definition d, Object value, long version) {
        return SystemSetting.createExisting(d.key, d.category, d.type, value, d.defaultValue, version, d.label, d.description, d.allowed, d.min, d.max);
    }
    private Definition definition(String key) {
        return DEFINITIONS.stream().filter(d -> d.key.equals(key)).findFirst().orElseThrow(() -> new SettingNotFoundException(key));
    }
    private Object decode(SettingType type, String value) {
        return switch (type) { case BOOLEAN -> Boolean.valueOf(value); case INTEGER -> Integer.valueOf(value); case STRING -> value; };
    }
}
