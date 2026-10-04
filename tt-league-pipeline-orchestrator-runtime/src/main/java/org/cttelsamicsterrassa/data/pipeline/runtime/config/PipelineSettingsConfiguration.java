package org.cttelsamicsterrassa.data.pipeline.runtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the nested settings as beans so the security, API, event and schedule layers do not depend on the whole
 * record.
 */
@Configuration(proxyBeanMethods = false)
public class PipelineSettingsConfiguration {

    @Bean
    PipelineOrchestratorProperties.Security securitySettings(PipelineOrchestratorProperties properties) {
        return properties.security();
    }

    @Bean
    PipelineOrchestratorProperties.Triggers triggerSettings(PipelineOrchestratorProperties properties) {
        return properties.triggers();
    }

    @Bean
    PipelineOrchestratorProperties.Events eventSettings(PipelineOrchestratorProperties properties) {
        return properties.events();
    }

    @Bean
    PipelineOrchestratorProperties.Schedule scheduleSettings(PipelineOrchestratorProperties properties) {
        return properties.schedule();
    }

    @Bean
    PipelineOrchestratorProperties.Tracker trackerSettings(PipelineOrchestratorProperties properties) {
        return properties.tracker();
    }
}
