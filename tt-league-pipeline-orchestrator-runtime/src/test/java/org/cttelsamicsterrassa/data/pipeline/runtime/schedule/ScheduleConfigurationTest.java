package org.cttelsamicsterrassa.data.pipeline.runtime.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerRepository;
import org.cttelsamicsterrassa.data.pipeline.runtime.api.RunDtoMapper;
import org.cttelsamicsterrassa.data.pipeline.runtime.api.TriggerConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineSettingsConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.events.RunEventsConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.execution.RunExecutionConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

/** Wiring without a database: the repositories and the data source are mocks. */
class ScheduleConfigurationTest {

    @TempDir
    Path artifacts;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class, RestClientAutoConfiguration.class))
                .withUserConfiguration(Repositories.class, RunExecutionConfiguration.class,
                        PipelineSettingsConfiguration.class, RunEventsConfiguration.class,
                        TriggerConfiguration.class, ScheduleConfiguration.class)
                .withPropertyValues(
                        "tt.pipeline.platform.base-url=http://localhost:8080",
                        "tt.pipeline.platform.api-key=platform-key",
                        "tt.pipeline.platform.connect-timeout=PT10S",
                        "tt.pipeline.platform.read-timeout=PT5M",
                        "tt.pipeline.platform.poll-interval=PT10S",
                        "tt.pipeline.ingest.base-url=http://localhost:8000",
                        "tt.pipeline.ingest.api-key=ingest-key",
                        "tt.pipeline.ingest.connect-timeout=PT10S",
                        "tt.pipeline.ingest.read-timeout=PT1M",
                        "tt.pipeline.ingest.poll-interval=PT15S",
                        "tt.pipeline.artifacts.dir=" + artifacts.toString().replace(java.io.File.separatorChar, '/'),
                        "tt.pipeline.execution.max-retries=3",
                        "tt.pipeline.execution.initial-backoff=PT30S",
                        "tt.pipeline.execution.backoff-multiplier=2",
                        "tt.pipeline.execution.max-backoff=PT5M",
                        "tt.pipeline.execution.timeouts.ingest=PT3H",
                        "tt.pipeline.execution.timeouts.fetch-package=PT10M",
                        "tt.pipeline.execution.timeouts.import-job=PT3H",
                        "tt.pipeline.execution.max-concurrent-runs=2",
                        "tt.pipeline.execution.recover-on-startup=false",
                        "tt.pipeline.security.jwt-secret=0123456789abcdef0123456789abcdef",
                        "tt.pipeline.triggers.conflict-mode=REJECT",
                        "tt.pipeline.events.heartbeat-interval=PT15S",
                        "tt.pipeline.events.emitter-timeout=PT30M",
                        "tt.pipeline.events.max-subscribers=50");
    }

    @Test
    void withoutSchedulesTheTriggerStartsWithNothingRegistered() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            ScheduledRunTrigger trigger = context.getBean(ScheduledRunTrigger.class);
            assertThat(trigger.isRunning()).isTrue();
            assertThat(trigger.registeredSources()).isEmpty();
            assertThat(context.getBean(LockProvider.class)).isInstanceOf(JdbcTemplateLockProvider.class);
        });
    }

    @Test
    void aConfiguredCronIsRegisteredAndStoppedWithTheContext() {
        ScheduledRunTrigger[] started = new ScheduledRunTrigger[1];
        runner().withPropertyValues(
                        "tt.pipeline.schedule.season=2025-2026",
                        "tt.pipeline.schedule.zone=Europe/Madrid",
                        "tt.pipeline.schedule.lock-at-most-for=PT10M",
                        "tt.pipeline.schedule.lock-at-least-for=PT30S",
                        "tt.pipeline.schedule.sources.BCNESA.cron=0 0 0 1 1 *")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    started[0] = context.getBean(ScheduledRunTrigger.class);
                    assertThat(started[0].registeredSources()).containsExactly(PipelineSource.BCNESA);
                });
        assertThat(started[0].isRunning()).isFalse();
        assertThat(started[0].schedulerActive()).isFalse();
    }

    @Test
    void theSchedulerIsNotExposedAsAnExecutorOrTaskSchedulerBean() {
        runner().withPropertyValues(
                        "tt.pipeline.schedule.season=2025-2026",
                        "tt.pipeline.schedule.zone=Europe/Madrid",
                        "tt.pipeline.schedule.lock-at-most-for=PT10M",
                        "tt.pipeline.schedule.lock-at-least-for=PT30S",
                        "tt.pipeline.schedule.sources.RFETM.cron=0 0 0 1 1 *")
                .run(context -> {
                    assertThat(context.getBeansOfType(Executor.class)).isEmpty();
                    assertThat(context.getBeansOfType(TaskScheduler.class)).isEmpty();
                });
    }

    @Configuration
    @EnableConfigurationProperties(PipelineOrchestratorProperties.class)
    static class Repositories {

        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        PendingTriggerRepository pendingTriggers() {
            return mock(PendingTriggerRepository.class);
        }

        @Bean
        RunDtoMapper runDtoMapper(RunClock clock) {
            return new RunDtoMapper(clock);
        }

        @Bean
        PipelineRunRepository runs() {
            return mock(PipelineRunRepository.class);
        }

        @Bean
        PipelineStepRepository steps() {
            return mock(PipelineStepRepository.class);
        }

        @Bean
        RunArtifactRepository artifactRows() {
            return mock(RunArtifactRepository.class);
        }

        @Bean
        ImportReportRepository reports() {
            return mock(ImportReportRepository.class);
        }
    }
}
