package org.cttelsamicsterrassa.data.core.domain.settings.repository;

import org.cttelsamicsterrassa.data.core.domain.settings.model.PersistedSetting;

import java.util.List;
import java.util.Map;

public interface SettingsRepository {
    List<PersistedSetting> findAll();

    void save(PersistedSetting setting, long expectedVersion);

    void replaceAll(Map<String, PersistedSetting> settings);
}
