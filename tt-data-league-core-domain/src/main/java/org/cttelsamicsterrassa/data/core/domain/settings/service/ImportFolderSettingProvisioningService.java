package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.ImportFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Ensures the {@code IMPORT/repository-folder} administrator setting always exists. Idempotent: it only
 * creates the setting when it is absent (with {@link ImportFolderSetting#DEFAULT_VALUE} or a caller-supplied
 * initial value), and never overwrites an administrator's configured value.
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
        return ensureExists(ImportFolderSetting.DEFAULT_VALUE);
    }

    /**
     * Creates the setting with {@code initialValue} when it is absent; an existing setting is returned unchanged.
     *
     * @throws IllegalArgumentException when {@code initialValue} is null or blank
     */
    public Setting ensureExists(String initialValue) {
        if (initialValue == null || initialValue.isBlank()) {
            throw new IllegalArgumentException("The initial import repository folder must not be blank");
        }
        return settingFinderService.findByCategoryAndName(ImportFolderSetting.CATEGORY, ImportFolderSetting.NAME)
                .orElseGet(() -> settingCreationService.create(
                        ImportFolderSetting.CATEGORY, ImportFolderSetting.NAME, initialValue));
    }
}
