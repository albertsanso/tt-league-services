package org.cttelsamicsterrassa.data.api.runtime.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the management endpoint exposure in {@code application.yml} (FEAT-00116): with Actuator on the
 * classpath, only health and info may be exposed on the management port, and the liveness/readiness probes
 * the container health check calls must be enabled.
 */
class ManagementEndpointExposureTest {

    @Test
    void exposesOnlyHealthAndInfoOverHttp() throws IOException {
        PropertySource<?> application = applicationYaml();

        assertEquals("health,info", application.getProperty("management.endpoints.web.exposure.include"));
        assertFalse(String.valueOf(application.getProperty("management.endpoints.web.exposure.include"))
                .contains("*"));
    }

    @Test
    void enablesHealthProbesWithoutLeakingDetailsToAnonymousCallers() throws IOException {
        PropertySource<?> application = applicationYaml();

        assertEquals(true, application.getProperty("management.endpoint.health.probes.enabled"));
        assertEquals("when-authorized", application.getProperty("management.endpoint.health.show-details"));
        assertEquals(9090, application.getProperty("management.server.port"));
    }

    @Test
    void doesNotLetAnUnreachableSmtpServerTurnHealthDown() throws IOException {
        assertEquals(false, applicationYaml().getProperty("management.health.mail.enabled"));
    }

    @Test
    void leavesTheInitialImportFolderUnsetByDefault() throws IOException {
        PropertySource<?> application = applicationYaml();

        assertEquals("${IMPORT_REPOSITORY_FOLDER_INITIAL:}",
                application.getProperty("tt.league.import.repository-folder-initial"));
    }

    private static PropertySource<?> applicationYaml() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        return sources.get(0);
    }
}
