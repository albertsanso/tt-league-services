package org.cttelsamicsterrassa.data.core.domain.settings.service;

public final class SettingConflictException extends SettingsException {
    public SettingConflictException(String key) {
        super("Setting was changed by another administrator: " + key);
    }
}
