package org.cttelsamicsterrassa.data.core.domain.settings.service;

public final class SettingNotFoundException extends SettingsException {
    public SettingNotFoundException(String key) {
        super("Unknown setting: " + key);
    }
}
