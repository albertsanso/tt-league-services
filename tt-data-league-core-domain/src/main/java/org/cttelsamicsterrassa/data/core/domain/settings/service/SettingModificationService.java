package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class SettingModificationService {
    private final SettingRepository settingRepository;

    @Inject
    public SettingModificationService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public Setting modifyValue(UUID id, String newValue) {
        return settingRepository.findById(id)
                .map(setting -> {
                    setting.modifyValue(newValue);
                    settingRepository.save(setting);
                    return setting;
                })
                .orElseThrow(() -> new IllegalArgumentException("Setting with id " + id + " not found."));
    }

    public Setting modifyValue(SettingCategory category, String name, String newValue) {
        return settingRepository.findByCategoryAndName(category.name(), name)
                .map(setting -> {
                    setting.modifyValue(newValue);
                    settingRepository.save(setting);
                    return setting;
                })
                .orElseThrow(() -> new IllegalArgumentException("Setting with category " + category + " and name " + name + " not found."));
    }
}
