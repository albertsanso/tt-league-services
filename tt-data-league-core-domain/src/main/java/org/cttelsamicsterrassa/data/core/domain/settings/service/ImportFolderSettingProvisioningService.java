package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.ImportFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Ensures the {@code IMPORT/import-folder} administrator setting always exists. Idempotent: it only
 * creates the setting with {@link ImportFolderSetting#DEFAULT_VALUE} when it is absent, and never
 * overwrites an administrator's configured value.
 */
@Named
public class ImportFolderSettingProvisioningService {

    private final SettingFinderService settingFinderService;
    private final SettingCreationService settingCreationService;

    @Inject
    public ImportFolderSettingProvisioningService(SettingFinderService settingFinderService,
                                                   SettingCreationService settingCreationService) {
        this.settingFinderService = settingFinderService;
        this.settingCreationService = settingCreationService;
    }

    public Setting ensureDefaultExists() {
        return settingFinderService.findByCategoryAndName(ImportFolderSetting.CATEGORY, ImportFolderSetting.NAME)
                .orElseGet(() -> settingCreationService.create(
                        ImportFolderSetting.CATEGORY, ImportFolderSetting.NAME, ImportFolderSetting.DEFAULT_VALUE));
    }
}
