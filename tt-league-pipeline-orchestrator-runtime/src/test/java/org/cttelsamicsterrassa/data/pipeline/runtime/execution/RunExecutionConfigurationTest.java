package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunRecovery;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.CompositeRunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerRepository;
import org.cttelsamicsterrassa.data.pipeline.runtime.api.RunDtoMapper;
import org.cttelsamicsterrassa.data.pipeline.runtime.api.TriggerConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineSettingsConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.events.RunEventsConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.tracker.TrackerConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.tracker.TrackerRecomputeDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayActions;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayTracker;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollPolicyRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollScheduleRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.runtime.notification.NotificationConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.polling.PollingConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.cttelsamicsterrassa.data.pipeline.runtime.metrics.RunMetricsObserver;
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

/** Wiring without a database: the repositories are mocks. */
class RunExecutionConfigurationTest {

    @TempDir
    Path artifacts;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class, RestClientAutoConfiguration.class))
                .withUserConfiguration(Repositories.class, RunExecutionConfiguration.class,
                        PipelineSettingsConfiguration.class, RunEventsConfiguration.class,
                        TriggerConfiguration.class, TrackerConfiguration.class,
                        PollingConfiguration.class, NotificationConfiguration.class)
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
                        "tt.pipeline.artifacts.dir=" + slashed(artifacts),
                        "tt.pipeline.execution.max-retries=3",
                        "tt.pipeline.execution.initial-backoff=PT30S",
                        "tt.pipeline.execution.backoff-multiplier=2",
                        "tt.pipeline.execution.max-backoff=PT5M",
                        "tt.pipeline.execution.timeouts.ingest=PT3H",
                        "tt.pipeline.execution.timeouts.fetch-package=PT10M",
                        "tt.pipeline.execution.timeouts.import-job=PT3H",
                        "tt.pipeline.execution.max-concurrent-runs=2",
                        "tt.pipeline.execution.recover-on-startup=false",
                        "tt.pipeline.statistics.zone=Europe/Madrid",
                        "tt.pipeline.security.jwt-secret=0123456789abcdef0123456789abcdef",
                        "tt.pipeline.triggers.conflict-mode=REJECT",
                        "tt.pipeline.events.heartbeat-interval=PT15S",
                        "tt.pipeline.events.emitter-timeout=PT30M",
                        "tt.pipeline.events.max-subscribers=50");
    }

    @Test
    void wiresTheExecutorLauncherAndRecovery() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RunExecutor.class);
            assertThat(context).hasSingleBean(RunLauncher.class);
            assertThat(context).hasSingleBean(RunRecovery.class);
            assertThat(context.getBean(RunDispatcher.class)).isInstanceOf(ExecutorRunDispatcher.class);
        });
    }

    @Test
    void theRunObserverIsPrimaryAndTheRunMetricsAreWired() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RunObserver.class)).isInstanceOf(CompositeRunObserver.class);
            assertThat(context).hasSingleBean(RunMetricsObserver.class);
        });
    }

    @Test
    void wiresTheTrackerAndItsRecomputeDispatcher() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MatchDayTracker.class);
            assertThat(context).hasSingleBean(MatchDayActions.class);
            assertThat(context.getBean(TrackerRecomputeDispatcher.class).isRunning()).isTrue();
        });
    }

    @Test
    void theDispatcherPoolIsNotExposedAsAnExecutorBean() {
        runner().run(context -> assertThat(context.getBeansOfType(Executor.class)).isEmpty());
    }

    @Test
    void startupFailsWhenTheArtifactDirectoryDoesNotExist() {
        runner().withPropertyValues("tt.pipeline.artifacts.dir=" + slashed(artifacts.resolve("missing")))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootMessages(context.getStartupFailure())).contains("missing");
                });
    }

    private static String slashed(Path path) {
        return path.toString().replace(java.io.File.separatorChar, '/');
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }

    @Configuration
    @EnableConfigurationProperties(PipelineOrchestratorProperties.class)
    static class Repositories {

        @Bean
        AlertRepository alertRepository() {
            return mock(AlertRepository.class);
        }

        @Bean
        MatchDayRepository matchDays() {
            return mock(MatchDayRepository.class);
        }

        @Bean
        LockingTaskExecutor schedulerLockingTaskExecutor() {
            return mock(LockingTaskExecutor.class);
        }

        @Bean
        PollPolicyRepository pollPolicies() {
            return mock(PollPolicyRepository.class);
        }

        @Bean
        PollScheduleRepository pollSchedules() {
            return mock(PollScheduleRepository.class);
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
        RunMetricsObserver runMetricsObserver(PipelineRunRepository runs) {
            return new RunMetricsObserver(new SimpleMeterRegistry(), runs);
        }

        @Bean
        PipelineStepRepository steps() {
            return mock(PipelineStepRepository.class);
        }

        @Bean
        RunUnitRepository units() {
            return mock(RunUnitRepository.class);
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
