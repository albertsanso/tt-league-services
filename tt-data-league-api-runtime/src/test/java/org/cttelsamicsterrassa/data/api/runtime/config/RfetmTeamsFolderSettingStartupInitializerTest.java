package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.settings.model.Setting;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;
import org.cttelsamicsterrassa.data.core.domain.settings.service.RfetmTeamsFolderSettingProvisioningService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RfetmTeamsFolderSettingStartupInitializerTest {

    @Test
    void delegatesProvisioningToTheDomainServiceOnStartup() throws Exception {
        RfetmTeamsFolderSettingProvisioningService provisioningService = mock(RfetmTeamsFolderSettingProvisioningService.class);
        Setting provisioned = Setting.createExisting(
                UUID.randomUUID(), SettingCategory.IMPORT, "rfetm-teams-folder", "import-rfetm\\teams");
        when(provisioningService.ensureDefaultExists()).thenReturn(provisioned);
        RfetmTeamsFolderSettingStartupInitializer initializer =
                new RfetmTeamsFolderSettingStartupInitializer(provisioningService);

        initializer.run();

        verify(provisioningService).ensureDefaultExists();
    }

    @Test
    void propagatesProvisioningFailuresInsteadOfSwallowingThem() {
        RfetmTeamsFolderSettingProvisioningService provisioningService = mock(RfetmTeamsFolderSettingProvisioningService.class);
        when(provisioningService.ensureDefaultExists()).thenThrow(new IllegalStateException("persistence unavailable"));
        RfetmTeamsFolderSettingStartupInitializer initializer =
                new RfetmTeamsFolderSettingStartupInitializer(provisioningService);

        assertThrows(IllegalStateException.class, initializer::run);
    }
}
