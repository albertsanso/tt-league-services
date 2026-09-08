package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.settings.service.RfetmTeamsFolderSettingProvisioningService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures the {@code IMPORT/rfetm-teams-folder} administrator setting exists as soon as the API runtime
 * starts. Delegates to {@link RfetmTeamsFolderSettingProvisioningService}, which is idempotent: repeated
 * launches never overwrite an administrator's configured value. Any persistence or initialization
 * failure propagates so the application fails to start rather than running unconfigured.
 */
@Component
public class RfetmTeamsFolderSettingStartupInitializer implements CommandLineRunner {

    private final RfetmTeamsFolderSettingProvisioningService provisioningService;

    public RfetmTeamsFolderSettingStartupInitializer(RfetmTeamsFolderSettingProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    public void run(String... args) {
        provisioningService.ensureDefaultExists();
    }
}
