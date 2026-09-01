package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Named
public class SettingFinderService {
    private final SettingRepository settingRepository;

    @Inject
    public SettingFinderService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public List<Setting> findAll() {
        return settingRepository.findAll();
    }

    public Setting findById(UUID id) {
        return settingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Setting with id " + id + " not found."));
    }

    public List<Setting> findByCategory(SettingCategory category) {
        return settingRepository.findAllByCategory(category);
    }

    public Optional<Setting> findByCategoryAndName(SettingCategory category, String name) {
        return settingRepository.findByCategoryAndName(category, name);
    }
}
