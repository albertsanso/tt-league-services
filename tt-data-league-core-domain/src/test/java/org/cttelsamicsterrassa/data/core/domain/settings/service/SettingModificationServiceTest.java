package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.ImportFolderSetting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingModificationServiceTest {

    private static final String NEW_REPOSITORY_FOLDER =
            Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("new-repository").toString();

    @Test
    void rejectsABlankImportFolderValueById() {
        UUID id = UUID.randomUUID();
        Setting existing = Setting.createExisting(id, SettingCategory.IMPORT, "repository-folder", ImportFolderSetting.DEFAULT_VALUE);
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findById(id)).thenReturn(Optional.of(existing));
        SettingModificationService service = new SettingModificationService(settingRepository);

        assertThrows(IllegalArgumentException.class, () -> service.modifyValue(id, ""));
        verify(settingRepository, never()).save(any(Setting.class));
    }

    @Test
    void rejectsABlankImportFolderValueByCategoryAndName() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        SettingModificationService service = new SettingModificationService(settingRepository);

        assertThrows(IllegalArgumentException.class,
                () -> service.modifyValue(SettingCategory.IMPORT, "repository-folder", " "));
        verify(settingRepository, never()).findByCategoryAndName(any(), any());
        verify(settingRepository, never()).save(any(Setting.class));
    }

    @Test
    void acceptsAValidImportFolderValue() {
        UUID id = UUID.randomUUID();
        Setting existing = Setting.createExisting(id, SettingCategory.IMPORT, "repository-folder", ImportFolderSetting.DEFAULT_VALUE);
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findById(id)).thenReturn(Optional.of(existing));
        SettingModificationService service = new SettingModificationService(settingRepository);

        Setting result = service.modifyValue(id, NEW_REPOSITORY_FOLDER);

        assertEquals(NEW_REPOSITORY_FOLDER, result.getValue());
        verify(settingRepository).save(existing);
    }

    @Test
    void rejectsABlankRfetmTeamsFolderValueByCategoryAndName() {
        SettingRepository settingRepository = mock(SettingRepository.class);
        SettingModificationService service = new SettingModificationService(settingRepository);

        assertThrows(IllegalArgumentException.class,
                () -> service.modifyValue(SettingCategory.IMPORT, "rfetm-teams-folder", " "));
        verify(settingRepository, never()).findByCategoryAndName(any(), any());
        verify(settingRepository, never()).save(any(Setting.class));
    }

    @Test
    void acceptsAValidRfetmTeamsFolderValue() {
        UUID id = UUID.randomUUID();
        Setting existing = Setting.createExisting(id, SettingCategory.IMPORT, "rfetm-teams-folder", "import-rfetm/teams");
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findById(id)).thenReturn(Optional.of(existing));
        SettingModificationService service = new SettingModificationService(settingRepository);

        Setting result = service.modifyValue(id, "other-rfetm/teams");

        assertEquals("other-rfetm/teams", result.getValue());
        verify(settingRepository).save(existing);
    }

    @Test
    void doesNotValidateSettingsOutsideTheImportFolderContract() {
        UUID id = UUID.randomUUID();
        Setting existing = Setting.createExisting(id, SettingCategory.GENERAL, "site.name", "League");
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findById(id)).thenReturn(Optional.of(existing));
        SettingModificationService service = new SettingModificationService(settingRepository);

        Setting result = service.modifyValue(id, "");

        assertEquals("", result.getValue());
    }
}
