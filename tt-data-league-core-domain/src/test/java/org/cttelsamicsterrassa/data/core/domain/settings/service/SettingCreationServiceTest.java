package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.ImportFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.RfetmTeamsFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SettingCreationServiceTest {

    @Test
    void createsSettingsThatAreNotTheImportFolder() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        SettingCreationService service = new SettingCreationService(settingRepository);

        Setting created = service.create(SettingCategory.GENERAL, "site.name", "League");

        assertEquals("League", created.getValue());
        verify(settingRepository).save(created);
    }

    @Test
    void rejectsABlankImportFolderValue() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        SettingCreationService service = new SettingCreationService(settingRepository);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(SettingCategory.IMPORT, "repository-folder", "   "));
        verify(settingRepository, never()).save(any(Setting.class));
    }

    @Test
    void acceptsAValidImportFolderValue() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        SettingCreationService service = new SettingCreationService(settingRepository);

        Setting created = service.create(SettingCategory.IMPORT, "repository-folder", ImportFolderSetting.DEFAULT_VALUE);

        assertEquals(ImportFolderSetting.DEFAULT_VALUE, created.getValue());
        verify(settingRepository).save(created);
    }

    @Test
    void rejectsABlankRfetmTeamsFolderValue() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        SettingCreationService service = new SettingCreationService(settingRepository);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(SettingCategory.IMPORT, "rfetm-teams-folder", "   "));
        verify(settingRepository, never()).save(any(Setting.class));
    }

    @Test
    void acceptsAValidRfetmTeamsFolderValue() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        SettingCreationService service = new SettingCreationService(settingRepository);

        Setting created = service.create(SettingCategory.IMPORT, "rfetm-teams-folder", RfetmTeamsFolderSetting.DEFAULT_VALUE);

        assertEquals(RfetmTeamsFolderSetting.DEFAULT_VALUE, created.getValue());
        verify(settingRepository).save(created);
    }
}
