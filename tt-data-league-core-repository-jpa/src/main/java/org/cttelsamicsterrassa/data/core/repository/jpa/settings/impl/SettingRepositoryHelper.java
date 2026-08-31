package org.cttelsamicsterrassa.data.core.repository.jpa.settings.impl;

import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.model.SettingJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingRepositoryHelper extends JpaRepository<SettingJPA, UUID> {
    Optional<SettingJPA> findByCategoryAndName(SettingCategory category, String name);

    List<SettingJPA> findAllByCategory(SettingCategory category);
}
