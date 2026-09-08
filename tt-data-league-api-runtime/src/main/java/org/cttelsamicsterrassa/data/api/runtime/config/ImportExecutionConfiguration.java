package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceProcessService;
import org.cttelsamicsterrassa.data.core.domain.settings.service.RfetmTeamsFolderPathResolver;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionService;
import org.cttelsamicsterrassa.data.load.shared.process.NavigatorBackedImportResourceProcessService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(ImportExecutionProperties.class)
public class ImportExecutionConfiguration {
    @Bean
    ImportExecutionOptions importExecutionOptions(ImportExecutionProperties properties) {
        return properties.toOptions();
    }

    @Bean
    @Primary
    ImportResourceProcessService importResourceProcessService(ImportExecutionService executionService,
                                                              ImportExecutionOptions options,
                                                              RfetmTeamsFolderPathResolver rfetmTeamsFolderResolver) {
        return new NavigatorBackedImportResourceProcessService(executionService, options,
                rfetmTeamsFolderResolver::resolve);
    }
}
