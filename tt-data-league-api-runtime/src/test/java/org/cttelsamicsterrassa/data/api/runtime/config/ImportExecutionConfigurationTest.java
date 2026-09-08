package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceProcessService;
import org.cttelsamicsterrassa.data.core.domain.settings.service.RfetmTeamsFolderPathResolver;
import org.cttelsamicsterrassa.data.core.domain.settings.service.SettingFinderService;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ImportExecutionConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ImportExecutionConfiguration.class, TestConfiguration.class)
            .withPropertyValues(
                    "tt.league.import.execution.batch-size=17",
                    "tt.league.import.execution.club-consolidation=REPORT",
                    "tt.league.import.execution.player-consolidation=WRITE");

    @Test
    void bindsServerPropertiesToExecutionOptions() {
        contextRunner.run(context -> {
            ImportExecutionOptions options = context.getBean(ImportExecutionOptions.class);

            assertThat(options.batchSize()).isEqualTo(17);
            assertThat(options.clubConsolidationMode()).isEqualTo(ConsolidationMode.REPORT);
            assertThat(options.playerConsolidationMode()).isEqualTo(ConsolidationMode.WRITE);
        });
    }

    @Test
    void wiresTheRfetmTeamsFolderResolverIntoTheProcessService() {
        contextRunner.run(context ->
                assertThat(context.getBean(ImportResourceProcessService.class)).isNotNull());
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        ImportExecutionService importExecutionService() {
            return (request, options) -> null;
        }

        @Bean
        RfetmTeamsFolderPathResolver rfetmTeamsFolderPathResolver() {
            return new RfetmTeamsFolderPathResolver(mock(SettingFinderService.class));
        }
    }
}
