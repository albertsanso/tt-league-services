package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.auth.user.service.InitialUserProvisioningService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InitialUserStartupInitializerTest {

    @Test
    void delegatesProvisioningToTheDomainServiceOnStartup() {
        InitialUserProvisioningService provisioningService = mock(InitialUserProvisioningService.class);
        InitialUserStartupInitializer initializer = new InitialUserStartupInitializer(provisioningService);

        initializer.run();

        verify(provisioningService).ensureDefaultUsersExist();
    }

    @Test
    void propagatesProvisioningFailuresInsteadOfSwallowingThem() {
        InitialUserProvisioningService provisioningService = mock(InitialUserProvisioningService.class);
        doThrow(new IllegalStateException("persistence unavailable")).when(provisioningService).ensureDefaultUsersExist();
        InitialUserStartupInitializer initializer = new InitialUserStartupInitializer(provisioningService);

        assertThrows(IllegalStateException.class, initializer::run);
    }
}
