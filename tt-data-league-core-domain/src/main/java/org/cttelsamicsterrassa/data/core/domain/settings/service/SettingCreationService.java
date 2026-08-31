package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class SettingCreationService {
    private final SettingRepository settingRepository;

    @Inject
    public SettingCreationService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public Setting create(SettingCategory category, String name, String value) {
        Setting setting = Setting.createNew(category, name, value);
        settingRepository.save(setting);
        return setting;
    }
}
