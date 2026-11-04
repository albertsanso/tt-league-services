package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.service.ImportFolderSettingProvisioningService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportFolderSettingStartupInitializerTest {

    @Test
    void delegatesProvisioningToTheDomainServiceOnStartup() throws Exception {
        ImportFolderSettingProvisioningService provisioningService = mock(ImportFolderSettingProvisioningService.class);
        Setting provisioned = Setting.createExisting(
                UUID.randomUUID(), SettingCategory.IMPORT, "repository-folder", "c:\\tt-repository");
        when(provisioningService.ensureDefaultExists()).thenReturn(provisioned);
        ImportFolderSettingStartupInitializer initializer =
                new ImportFolderSettingStartupInitializer(provisioningService, "");

        initializer.run();

        verify(provisioningService).ensureDefaultExists();
    }

    @Test
    void propagatesProvisioningFailuresInsteadOfSwallowingThem() {
        ImportFolderSettingProvisioningService provisioningService = mock(ImportFolderSettingProvisioningService.class);
        when(provisioningService.ensureDefaultExists()).thenThrow(new IllegalStateException("persistence unavailable"));
        ImportFolderSettingStartupInitializer initializer =
                new ImportFolderSettingStartupInitializer(provisioningService, "");

        assertThrows(IllegalStateException.class, initializer::run);
    }

    @Test
    void usesTheConfiguredInitialFolderWhenSet() {
        ImportFolderSettingProvisioningService provisioningService = mock(ImportFolderSettingProvisioningService.class);
        String folder = Path.of("repository").toAbsolutePath().toString();
        ImportFolderSettingStartupInitializer initializer =
                new ImportFolderSettingStartupInitializer(provisioningService, folder);

        initializer.run();

        verify(provisioningService).ensureExists(folder);
        verify(provisioningService, never()).ensureDefaultExists();
    }

    @Test
    void usesTheDefaultWhenTheInitialFolderIsBlank() {
        ImportFolderSettingProvisioningService provisioningService = mock(ImportFolderSettingProvisioningService.class);
        ImportFolderSettingStartupInitializer initializer =
                new ImportFolderSettingStartupInitializer(provisioningService, "   ");

        initializer.run();

        verify(provisioningService).ensureDefaultExists();
        verify(provisioningService, never()).ensureExists(anyString());
    }

    @Test
    void failsStartupWhenTheInitialFolderIsNotAnAbsolutePath() {
        ImportFolderSettingProvisioningService provisioningService = mock(ImportFolderSettingProvisioningService.class);
        ImportFolderSettingStartupInitializer initializer =
                new ImportFolderSettingStartupInitializer(provisioningService, "relative/repository");

        IllegalStateException failure = assertThrows(IllegalStateException.class, initializer::run);

        assertTrue(failure.getMessage().contains("IMPORT_REPOSITORY_FOLDER_INITIAL"));
        verify(provisioningService, never()).ensureExists(anyString());
        verify(provisioningService, never()).ensureDefaultExists();
    }
}
