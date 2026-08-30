package org.cttelsamicsterrassa.data.api.rest.settings;

import org.cttelsamicsterrassa.data.core.domain.settings.repository.SettingsRepository;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SystemSettingsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SettingsConfig {
    @Bean
    public SystemSettingsService systemSettingsService(SettingsRepository repository) {
        return new SystemSettingsService(repository);
    }
}
