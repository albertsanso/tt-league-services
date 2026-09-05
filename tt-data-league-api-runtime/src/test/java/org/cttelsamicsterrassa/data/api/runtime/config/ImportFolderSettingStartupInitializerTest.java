package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.service.ImportFolderSettingProvisioningService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportFolderSettingStartupInitializerTest {

    @Test
    void delegatesProvisioningToTheDomainServiceOnStartup() throws Exception {
        ImportFolderSettingProvisioningService provisioningService = mock(ImportFolderSettingProvisioningService.class);
        Setting provisioned = Setting.createExisting(
                UUID.randomUUID(), SettingCategory.IMPORT, "import-folder", "c:\\tt-repository");
        when(provisioningService.ensureDefaultExists()).thenReturn(provisioned);
        ImportFolderSettingStartupInitializer initializer =
                new ImportFolderSettingStartupInitializer(provisioningService);

        initializer.run();

        verify(provisioningService).ensureDefaultExists();
    }

    @Test
    void propagatesProvisioningFailuresInsteadOfSwallowingThem() {
        ImportFolderSettingProvisioningService provisioningService = mock(ImportFolderSettingProvisioningService.class);
        when(provisioningService.ensureDefaultExists()).thenThrow(new IllegalStateException("persistence unavailable"));
        ImportFolderSettingStartupInitializer initializer =
                new ImportFolderSettingStartupInitializer(provisioningService);

        assertThrows(IllegalStateException.class, initializer::run);
    }
}
