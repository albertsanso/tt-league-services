package org.cttelsamicsterrassa.data.core.domain.settings.repository;

import org.cttelsamicsterrassa.data.core.domain.settings.model.SystemSetting;

import java.util.List;
import java.util.Map;

public interface SettingsRepository {
    List<SystemSetting> findAll();

    void save(SystemSetting setting, long expectedVersion);

    void replaceAll(Map<String, SystemSetting> settings);

    default void replaceAll(Map<String, SystemSetting> settings, Map<String, Long> expectedVersions) {
        replaceAll(settings);
    }
}
