package org.cttelsamicsterrassa.data.pipeline.runtime.config;

import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsSettings;
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
    StatisticsSettings statisticsSettings(PipelineOrchestratorProperties properties) {
        return properties.statisticsSettings();
    }

    @Bean
    PipelineOrchestratorProperties.Schedule scheduleSettings(PipelineOrchestratorProperties properties) {
        return properties.schedule();
    }

    @Bean
    PipelineOrchestratorProperties.Tracker trackerSettings(PipelineOrchestratorProperties properties) {
        return properties.tracker();
    }

    @Bean
    PipelineOrchestratorProperties.Polling pollingSettings(PipelineOrchestratorProperties properties) {
        return properties.polling();
    }

    @Bean
    PipelineOrchestratorProperties.Notifications notificationSettings(PipelineOrchestratorProperties properties) {
        return properties.notifications();
    }
}
