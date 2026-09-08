package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.ImportFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.RfetmTeamsFolderSetting;
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
        if (ImportFolderSetting.matches(category, name)) {
            ImportFolderSetting.validate(value);
        }
        if (RfetmTeamsFolderSetting.matches(category, name)) {
            RfetmTeamsFolderSetting.validate(value);
        }
        Setting setting = Setting.createNew(category, name, value);
        settingRepository.save(setting);
        return setting;
    }
}
