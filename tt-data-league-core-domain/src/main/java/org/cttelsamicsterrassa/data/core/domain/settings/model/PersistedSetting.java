package org.cttelsamicsterrassa.data.core.domain.settings.model;

public record PersistedSetting(String key, SettingType type, String value, long version) {
}
