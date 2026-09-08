package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.ImportFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.RfetmTeamsFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;

/**
 * Resolves the absolute RFETM teams folder used by club consolidation, by combining the persisted
 * {@code IMPORT/repository-folder} setting (the repository root) with the persisted
 * {@code IMPORT/rfetm-teams-folder} setting (the path to the teams data, relative to that root).
 * Both settings are administrator-configured and re-read on every call so changes take effect without
 * restarting the application.
 */
@Named
public class RfetmTeamsFolderPathResolver {

    private final SettingFinderService settingFinderService;

    @Inject
    public RfetmTeamsFolderPathResolver(SettingFinderService settingFinderService) {
        this.settingFinderService = settingFinderService;
    }

    public Path resolve() {
        String repositoryFolder = requireSetting(ImportFolderSetting.CATEGORY, ImportFolderSetting.NAME);
        String rfetmTeamsFolder = requireSetting(RfetmTeamsFolderSetting.CATEGORY, RfetmTeamsFolderSetting.NAME);
        return Path.of(repositoryFolder).resolve(rfetmTeamsFolder);
    }

    private String requireSetting(SettingCategory category, String name) {
        return settingFinderService.findByCategoryAndName(category, name)
                .orElseThrow(() -> new IllegalStateException("Setting " + category + "/" + name + " is required"))
                .getValue();
    }
}
