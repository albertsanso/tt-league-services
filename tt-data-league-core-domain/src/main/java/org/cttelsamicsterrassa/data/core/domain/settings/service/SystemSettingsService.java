package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.PersistedSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingType;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SystemSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingsRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SystemSettingsService {
    private record Definition(String key, SettingCategory category, SettingType type, Object defaultValue,
                              String label, String description, List<String> allowed, Integer min, Integer max) {
    }

    private static final List<Definition> CATALOG = List.of(
            new Definition("ui.theme", SettingCategory.UI, SettingType.STRING, "light", "Theme",
                    "Default application theme", List.of("light", "dark", "system"), null, null),
            new Definition("ui.compactMode", SettingCategory.UI, SettingType.BOOLEAN, false, "Compact mode",
                    "Use compact spacing in the application", List.of(), null, null),
            new Definition("notifications.emailEnabled", SettingCategory.NOTIFICATIONS, SettingType.BOOLEAN, true,
                    "Email notifications", "Enable application email notifications", List.of(), null, null),
            new Definition("notifications.inAppEnabled", SettingCategory.NOTIFICATIONS, SettingType.BOOLEAN, true,
                    "In-app notifications", "Enable in-app notifications", List.of(), null, null),
            new Definition("notifications.importCompleted", SettingCategory.NOTIFICATIONS, SettingType.BOOLEAN, true,
                    "Import completion notifications", "Notify administrators when an import completes", List.of(), null, null),
            new Definition("import.autoValidate", SettingCategory.IMPORT, SettingType.BOOLEAN, true,
                    "Validate imports automatically", "Validate imported data before processing", List.of(), null, null),
            new Definition("import.preserveHistory", SettingCategory.IMPORT, SettingType.BOOLEAN, true,
                    "Preserve import history", "Keep existing season and match history during imports", List.of(), null, null),
            new Definition("import.maxBatchSize", SettingCategory.IMPORT, SettingType.INTEGER, 1000,
                    "Maximum import batch", "Maximum records accepted in one import batch", List.of(), 1, 10000),
            new Definition("display.maxSearchResults", SettingCategory.DISPLAY, SettingType.INTEGER, 50,
                    "Maximum search results", "Maximum results displayed by searches", List.of(), 10, 100),
            new Definition("display.maxPageSize", SettingCategory.DISPLAY, SettingType.INTEGER, 50,
                    "Maximum page size", "Maximum records displayed on one page", List.of(), 10, 100)
    );

    private final SettingsRepository repository;

    public SystemSettingsService(SettingsRepository repository) {
        this.repository = repository;
    }

    public synchronized List<SystemSetting> list(SettingCategory category, String search) {
        Map<String, PersistedSetting> persisted = persisted();
        String needle = search == null ? "" : search.trim().toLowerCase();
        return CATALOG.stream()
                .filter(d -> category == null || d.category == category)
                .filter(d -> needle.isEmpty() || d.key.toLowerCase().contains(needle)
                        || d.label.toLowerCase().contains(needle))
                .map(d -> toReadModel(d, persisted.get(d.key)))
                .toList();
    }

    public synchronized SystemSetting update(String key, Object value, long expectedVersion) {
        Definition definition = definition(key);
        Object validated = validate(definition, value);
        PersistedSetting current = persisted().get(key);
        long actualVersion = current == null ? 0 : current.version();
        if (actualVersion != expectedVersion) {
            throw new SettingConflictException(key);
        }
        long nextVersion = actualVersion + 1;
        repository.save(new PersistedSetting(key, definition.type, encode(definition.type, validated), nextVersion),
                expectedVersion);
        return toReadModel(definition, new PersistedSetting(key, definition.type, encode(definition.type, validated), nextVersion));
    }

    public synchronized Map<String, String> validate(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new SettingValidationException("At least one setting is required");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            try {
                validate(definition(entry.getKey()), entry.getValue());
            } catch (SettingsException e) {
                errors.put(entry.getKey(), e.getMessage());
            }
        }
        return Collections.unmodifiableMap(errors);
    }

    public synchronized List<SystemSetting> bulkUpdate(Map<String, Object> changes, Map<String, Long> expectedVersions) {
        Map<String, String> errors = validate(changes);
        if (!errors.isEmpty()) {
            throw new SettingValidationException(errors.toString());
        }
        Map<String, PersistedSetting> current = persisted();
        Map<String, PersistedSetting> next = new LinkedHashMap<>(current);
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            Definition definition = definition(entry.getKey());
            long actual = current.containsKey(entry.getKey()) ? current.get(entry.getKey()).version() : 0;
            long expected = expectedVersions == null || !expectedVersions.containsKey(entry.getKey())
                    ? actual : expectedVersions.get(entry.getKey());
            if (actual != expected) {
                throw new SettingConflictException(entry.getKey());
            }
            Object value = validate(definition, entry.getValue());
            next.put(entry.getKey(), new PersistedSetting(entry.getKey(), definition.type,
                    encode(definition.type, value), actual + 1));
        }
        repository.replaceAll(next);
        return list(null, null);
    }

    public synchronized List<SystemSetting> preview(Map<String, Object> changes) {
        Map<String, String> errors = validate(changes);
        if (!errors.isEmpty()) {
            throw new SettingValidationException(errors.toString());
        }
        Map<String, PersistedSetting> persisted = persisted();
        List<SystemSetting> result = new ArrayList<>();
        for (Definition definition : CATALOG) {
            if (changes.containsKey(definition.key)) {
                Object value = validate(definition, changes.get(definition.key));
                PersistedSetting existing = persisted.get(definition.key);
                result.add(toReadModel(definition, new PersistedSetting(definition.key, definition.type,
                        encode(definition.type, value), existing == null ? 0 : existing.version())));
            }
        }
        return List.copyOf(result);
    }

    public synchronized Map<String, Object> backup() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (SystemSetting setting : list(null, null)) {
            values.put(setting.key(), setting.value());
        }
        return Collections.unmodifiableMap(values);
    }

    public synchronized void restore(Map<String, Object> values) {
        Map<String, String> errors = validate(values);
        if (values.size() != CATALOG.size()) {
            throw new SettingValidationException("Backup must contain the complete supported catalog");
        }
        if (!errors.isEmpty()) {
            throw new SettingValidationException(errors.toString());
        }
        Map<String, PersistedSetting> replacement = new LinkedHashMap<>();
        for (Definition definition : CATALOG) {
            Object value = validate(definition, values.get(definition.key));
            long version = persisted().containsKey(definition.key) ? persisted().get(definition.key).version() + 1 : 1;
            replacement.put(definition.key, new PersistedSetting(definition.key, definition.type,
                    encode(definition.type, value), version));
        }
        repository.replaceAll(replacement);
    }

    private Map<String, PersistedSetting> persisted() {
        Map<String, PersistedSetting> result = new LinkedHashMap<>();
        repository.findAll().forEach(value -> result.put(value.key(), value));
        return result;
    }

    private Definition definition(String key) {
        return CATALOG.stream().filter(d -> d.key.equals(key)).findFirst()
                .orElseThrow(() -> new SettingNotFoundException(key));
    }

    private Object validate(Definition definition, Object value) {
        if (value == null) throw new SettingValidationException("Value is required");
        if (definition.type == SettingType.BOOLEAN && !(value instanceof Boolean)) {
            throw new SettingValidationException("Expected a boolean");
        }
        if (definition.type == SettingType.INTEGER
                && (!(value instanceof Number) || value instanceof Double || value instanceof Float
                || ((Number) value).longValue() != ((Number) value).doubleValue())) {
            throw new SettingValidationException("Expected an integer");
        }
        if (definition.type == SettingType.STRING && !(value instanceof String)) {
            throw new SettingValidationException("Expected a string");
        }
        if (definition.type == SettingType.INTEGER) {
            int number = ((Number) value).intValue();
            if (definition.min != null && number < definition.min || definition.max != null && number > definition.max) {
                throw new SettingValidationException("Value must be between " + definition.min + " and " + definition.max);
            }
        }
        if (!definition.allowed.isEmpty() && !definition.allowed.contains(value)) {
            throw new SettingValidationException("Value is not supported");
        }
        return value;
    }

    private SystemSetting toReadModel(Definition definition, PersistedSetting persisted) {
        Object value = persisted == null ? definition.defaultValue : decode(definition.type, persisted.value());
        return new SystemSetting(definition.key, definition.category, definition.type, value, definition.defaultValue,
                persisted == null ? 0 : persisted.version(), definition.label, definition.description,
                definition.allowed, definition.min, definition.max);
    }

    private String encode(SettingType type, Object value) {
        return String.valueOf(value);
    }

    private Object decode(SettingType type, String value) {
        return switch (type) {
            case BOOLEAN -> Boolean.valueOf(value);
            case INTEGER -> Integer.valueOf(value);
            case STRING -> value;
        };
    }
}
