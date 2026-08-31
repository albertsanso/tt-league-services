package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
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

    public List<Setting> findByCategory(String category) {
        return settingRepository.findAllByCategory(category);
    }

    public List<Setting> findByCategoryAndName(String category, String name) {
        return settingRepository.findByCategoryAndName(category, name)
                .map(List::of)
                .orElseThrow(() -> new IllegalArgumentException("Setting with category " + category + " and name " + name + " not found."));
    }
}
