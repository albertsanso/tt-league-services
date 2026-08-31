package org.cttelsamicsterrassa.data.core.repository.jpa.settings.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SystemSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingsRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.mapper.PersistedSettingToSystemSettingMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.mapper.SystemSettingToPersistedSettingMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.model.PersistedSetting;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component @Transactional
public class SettingsRepositoryJpa implements SettingsRepository {
    private final SettingRepositoryHelper helper;
    private final EntityManager entityManager;
    private final SystemSettingToPersistedSettingMapper toPersistence;
    private final PersistedSettingToSystemSettingMapper toDomain;
    public SettingsRepositoryJpa(SettingRepositoryHelper helper, EntityManager entityManager,
                                 SystemSettingToPersistedSettingMapper toPersistence,
                                 PersistedSettingToSystemSettingMapper toDomain) {
        this.helper=helper; this.entityManager=entityManager; this.toPersistence=toPersistence; this.toDomain=toDomain;
    }
    public List<SystemSetting> findAll() { return helper.findAll().stream().map(toDomain).toList(); }
    public void save(SystemSetting setting, long expectedVersion) {
        PersistedSetting current=helper.findById(setting.getKey()).orElse(null);
        if(current==null) {
            if(expectedVersion!=0) throw new IllegalStateException("Setting version conflict: "+setting.getKey());
            try {
                entityManager.persist(toPersistence.apply(setting));
                entityManager.flush();
            } catch (PersistenceException exception) {
                throw new IllegalStateException("Setting version conflict: " + setting.getKey(), exception);
            }
            return;
        }
        if(helper.updateIfVersion(setting.getKey(),setting.getType().name(),String.valueOf(setting.getValue()),setting.getVersion(),expectedVersion)!=1)
            throw new IllegalStateException("Setting version conflict: "+setting.getKey());
    }
    public void replaceAll(Map<String,SystemSetting> settings) {
        helper.deleteAllInBatch(); entityManager.flush(); entityManager.clear();
        settings.values().forEach(s->entityManager.persist(toPersistence.apply(s)));
    }
    @Override
    public void replaceAll(Map<String, SystemSetting> settings, Map<String, Long> expectedVersions) {
        for (Map.Entry<String, Long> expected : expectedVersions.entrySet()) {
            PersistedSetting current = helper.findLockedByKey(expected.getKey()).orElse(null);
            long actual = current == null ? 0 : current.getVersion();
            if (actual != expected.getValue()) {
                throw new IllegalStateException("Setting version conflict: " + expected.getKey());
            }
        }
        replaceAll(settings);
    }
}
