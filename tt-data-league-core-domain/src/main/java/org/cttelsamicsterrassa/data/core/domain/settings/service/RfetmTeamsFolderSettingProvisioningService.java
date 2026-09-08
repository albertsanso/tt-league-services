package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.RfetmTeamsFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Ensures the {@code IMPORT/rfetm-teams-folder} administrator setting always exists. Idempotent: it only
 * creates the setting with {@link RfetmTeamsFolderSetting#DEFAULT_VALUE} when it is absent, and never
 * overwrites an administrator's configured value.
 */
@Named
public class RfetmTeamsFolderSettingProvisioningService {

    private final SettingFinderService settingFinderService;
    private final SettingCreationService settingCreationService;

    @Inject
    public RfetmTeamsFolderSettingProvisioningService(SettingFinderService settingFinderService,
                                                        SettingCreationService settingCreationService) {
        this.settingFinderService = settingFinderService;
        this.settingCreationService = settingCreationService;
    }

    public Setting ensureDefaultExists() {
        return settingFinderService.findByCategoryAndName(RfetmTeamsFolderSetting.CATEGORY, RfetmTeamsFolderSetting.NAME)
                .orElseGet(() -> settingCreationService.create(
                        RfetmTeamsFolderSetting.CATEGORY, RfetmTeamsFolderSetting.NAME, RfetmTeamsFolderSetting.DEFAULT_VALUE));
    }
}
