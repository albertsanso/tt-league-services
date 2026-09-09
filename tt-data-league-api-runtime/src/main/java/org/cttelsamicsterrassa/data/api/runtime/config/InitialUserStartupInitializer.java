package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.auth.user.service.InitialUserProvisioningService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures the fixed set of default ADMIN accounts exists as soon as the API runtime starts.
 * Delegates to {@link InitialUserProvisioningService}, which is idempotent: repeated
 * launches never create duplicates. Any persistence or initialization failure propagates so
 * the application fails to start rather than running without the expected admins.
 */
@Component
public class InitialUserStartupInitializer implements CommandLineRunner {

    private final InitialUserProvisioningService provisioningService;

    public InitialUserStartupInitializer(InitialUserProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    public void run(String... args) {
        provisioningService.ensureDefaultUsersExist();
    }
}
