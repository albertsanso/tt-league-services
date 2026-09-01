package org.cttelsamicsterrassa.data.core.domain.settings.repository;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingRepository {

    Optional<Setting> findById(UUID id);
    Optional<Setting> findByCategoryAndName(SettingCategory category, String name);

    List<Setting> findAllByCategory(SettingCategory category);
    List<Setting> findAll();

    void delete(Setting setting);
    void save(Setting setting);
}
