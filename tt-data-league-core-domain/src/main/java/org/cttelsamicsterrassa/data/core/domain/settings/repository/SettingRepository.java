package org.cttelsamicsterrassa.data.core.domain.settings.repository;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingRepository {

    Optional<Setting> findById(UUID id);
    Optional<Setting> findByCategoryAndName(String category, String name);

    List<Setting> findAllByCategory(String category);
    List<Setting> findAll();

    void delete(Setting setting);
    void save(Setting setting);
}
