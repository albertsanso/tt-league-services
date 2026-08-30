package org.cttelsamicsterrassa.data.core.repository.jpa.settings.impl;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.cttelsamicsterrassa.data.core.domain.settings.model.PersistedSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingType;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingsRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.model.SettingJPA;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Transactional
public class SettingsRepositoryJpa implements SettingsRepository {
    private final SettingRepositoryHelper helper;
    private final EntityManager entityManager;

    public SettingsRepositoryJpa(SettingRepositoryHelper helper, EntityManager entityManager) {
        this.helper = helper;
        this.entityManager = entityManager;
    }

    @Override
    public List<PersistedSetting> findAll() {
        return helper.findAll().stream()
                .map(s -> new PersistedSetting(s.getKey(), SettingType.valueOf(s.getType()), s.getValue(), s.getVersion()))
                .toList();
    }

    @Override
    public void save(PersistedSetting setting, long expectedVersion) {
        SettingJPA current = helper.findById(setting.key()).orElse(null);
        if (current == null) {
            if (expectedVersion != 0) {
                throw new IllegalStateException("Setting version conflict: " + setting.key());
            }
            entityManager.persist(new SettingJPA(setting.key(), setting.type(), setting.value(), setting.version()));
            return;
        }
        if (helper.updateIfVersion(setting.key(), setting.type().name(), setting.value(),
                setting.version(), expectedVersion) != 1) {
            throw new IllegalStateException("Setting version conflict: " + setting.key());
        }
    }

    @Override
    public void replaceAll(Map<String, PersistedSetting> settings) {
        helper.deleteAllInBatch();
        entityManager.flush();
        entityManager.clear();
        settings.values().forEach(s -> entityManager.persist(
                new SettingJPA(s.key(), s.type(), s.value(), s.version())));
    }
}
