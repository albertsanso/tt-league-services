package org.cttelsamicsterrassa.data.core.repository.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

/**
 * {@code JpaTestApplication} component-scans the domain module's {@code @Named} application
 * services, some of which need beans only the API runtime supplies. Import this into any
 * {@code @SpringBootTest} that boots the full context, so those services can be instantiated
 * without pulling in the API runtime module.
 */
@TestConfiguration(proxyBeanMethods = false)
public class JpaTestSupportConfiguration {

    @Bean
    ImportRunRegistry importRunRegistry() {
        return mock(ImportRunRegistry.class);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
