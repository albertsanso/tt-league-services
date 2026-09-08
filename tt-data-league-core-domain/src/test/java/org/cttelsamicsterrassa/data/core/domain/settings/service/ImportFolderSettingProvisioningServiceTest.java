package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.ImportFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportFolderSettingProvisioningServiceTest {

    @Test
    void createsTheDefaultSettingWhenAbsent() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "repository-folder"))
                .thenReturn(Optional.empty());
        SettingFinderService finderService = new SettingFinderService(settingRepository);
        SettingCreationService creationService = new SettingCreationService(settingRepository);
        ImportFolderSettingProvisioningService provisioningService =
                new ImportFolderSettingProvisioningService(finderService, creationService);

        Setting created = provisioningService.ensureDefaultExists();

        assertEquals(SettingCategory.IMPORT, created.getSettingCategory());
        assertEquals("repository-folder", created.getName());
        assertEquals(ImportFolderSetting.DEFAULT_VALUE, created.getValue());
        verify(settingRepository).save(any(Setting.class));
    }

    @Test
    void isANoOpWhenTheSettingAlreadyExists() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        Setting existing = Setting.createExisting(
                UUID.randomUUID(), SettingCategory.IMPORT, "repository-folder", "d:\\custom-repository");
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "repository-folder"))
                .thenReturn(Optional.of(existing));
        SettingFinderService finderService = new SettingFinderService(settingRepository);
        SettingCreationService creationService = new SettingCreationService(settingRepository);
        ImportFolderSettingProvisioningService provisioningService =
                new ImportFolderSettingProvisioningService(finderService, creationService);

        Setting result = provisioningService.ensureDefaultExists();

        assertEquals("d:\\custom-repository", result.getValue());
        verify(settingRepository, never()).save(any(Setting.class));
    }
}
