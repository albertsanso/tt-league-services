package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.RfetmTeamsFolderSetting;
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

class RfetmTeamsFolderSettingProvisioningServiceTest {

    @Test
    void createsTheDefaultSettingWhenAbsent() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "rfetm-teams-folder"))
                .thenReturn(Optional.empty());
        SettingFinderService finderService = new SettingFinderService(settingRepository);
        SettingCreationService creationService = new SettingCreationService(settingRepository);
        RfetmTeamsFolderSettingProvisioningService provisioningService =
                new RfetmTeamsFolderSettingProvisioningService(finderService, creationService);

        Setting created = provisioningService.ensureDefaultExists();

        assertEquals(SettingCategory.IMPORT, created.getSettingCategory());
        assertEquals("rfetm-teams-folder", created.getName());
        assertEquals(RfetmTeamsFolderSetting.DEFAULT_VALUE, created.getValue());
        verify(settingRepository).save(any(Setting.class));
    }

    @Test
    void isANoOpWhenTheSettingAlreadyExists() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        Setting existing = Setting.createExisting(
                UUID.randomUUID(), SettingCategory.IMPORT, "rfetm-teams-folder", "d:\\custom-rfetm\\teams");
        when(settingRepository.findByCategoryAndName(SettingCategory.IMPORT, "rfetm-teams-folder"))
                .thenReturn(Optional.of(existing));
        SettingFinderService finderService = new SettingFinderService(settingRepository);
        SettingCreationService creationService = new SettingCreationService(settingRepository);
        RfetmTeamsFolderSettingProvisioningService provisioningService =
                new RfetmTeamsFolderSettingProvisioningService(finderService, creationService);

        Setting result = provisioningService.ensureDefaultExists();

        assertEquals("d:\\custom-rfetm\\teams", result.getValue());
        verify(settingRepository, never()).save(any(Setting.class));
    }
}
