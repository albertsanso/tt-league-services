package org.cttelsamicsterrassa.data.core.repository.jpa.settings.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.mapper.SettingJPAToSettingMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.settings.mapper.SettingToSettingJPAMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class SettingRepositoryJpa implements SettingRepository {
    private final SettingRepositoryHelper settingRepositoryHelper;
    private final SettingJPAToSettingMapper settingJPAToSettingMapper;
    private final SettingToSettingJPAMapper settingToSettingJPAMapper;

    @Override
    public Optional<Setting> findById(UUID id) {
        return settingRepositoryHelper.findById(id).map(settingJPAToSettingMapper);
    }

    @Override
    public Optional<Setting> findByCategoryAndName(SettingCategory category, String name) {
        return settingRepositoryHelper.findByCategoryAndName(category, name)
                .map(settingJPAToSettingMapper);
    }

    @Override
    public List<Setting> findAllByCategory(SettingCategory category) {
        return settingRepositoryHelper.findAllByCategory(category)
                .stream()
                .map(settingJPAToSettingMapper)
                .toList();
    }

    @Override
    public List<Setting> findAll() {
        return settingRepositoryHelper.findAll()
                .stream()
                .map(settingJPAToSettingMapper)
                .toList();
    }

    @Override
    public void delete(Setting setting) {
        settingRepositoryHelper.delete(settingToSettingJPAMapper.apply(setting));
    }

    @Override
    public void save(Setting setting) {
        settingRepositoryHelper.save(settingToSettingJPAMapper.apply(setting));
    }
}
