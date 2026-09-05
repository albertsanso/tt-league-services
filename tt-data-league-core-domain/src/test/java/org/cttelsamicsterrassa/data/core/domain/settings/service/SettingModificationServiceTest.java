package org.cttelsamicsterrassa.data.core.domain.settings.service;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingRepository;
import org.junit.jupiter.api.Test;

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

    @Test
    void rejectsABlankImportFolderValueById() {
        UUID id = UUID.randomUUID();
        Setting existing = Setting.createExisting(id, SettingCategory.IMPORT, "import-folder", "c:\\tt-repository");
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
                () -> service.modifyValue(SettingCategory.IMPORT, "import-folder", " "));
        verify(settingRepository, never()).findByCategoryAndName(any(), any());
        verify(settingRepository, never()).save(any(Setting.class));
    }

    @Test
    void acceptsAValidImportFolderValue() {
        UUID id = UUID.randomUUID();
        Setting existing = Setting.createExisting(id, SettingCategory.IMPORT, "import-folder", "c:\\tt-repository");
        SettingRepository settingRepository = mock(SettingRepository.class);
        when(settingRepository.findById(id)).thenReturn(Optional.of(existing));
        SettingModificationService service = new SettingModificationService(settingRepository);

        Setting result = service.modifyValue(id, "d:\\new-repository");

        assertEquals("d:\\new-repository", result.getValue());
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
