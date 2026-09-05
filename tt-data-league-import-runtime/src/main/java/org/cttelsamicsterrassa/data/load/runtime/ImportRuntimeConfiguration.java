package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceProcessService;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionService;
import org.cttelsamicsterrassa.data.load.shared.process.NavigatorBackedImportResourceProcessService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ImportRuntimeConfiguration {
    @Bean
    @Primary
    ImportResourceProcessService importResourceProcessService(ImportExecutionService executionService) {
        return new NavigatorBackedImportResourceProcessService(executionService, ImportExecutionOptions.defaults());
    }
}
