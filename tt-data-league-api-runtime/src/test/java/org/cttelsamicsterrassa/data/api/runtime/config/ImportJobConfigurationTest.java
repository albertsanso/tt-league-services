package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.api.runtime.importjob.ExecutorImportJobDispatcher;
import org.cttelsamicsterrassa.data.core.application.importjob.SubmitImportJobCommandHandler;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobRepository;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobService;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobSettings;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobStatus;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceRunService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceRepositoryLoaderService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceUploadService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceZipService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * FEAT-00100: the job wiring binds its settings, builds the job service and submit handler, recovers jobs once the
 * singletons exist, and leaves Spring Boot's {@code applicationTaskExecutor} as the only {@link Executor}, which the
 * manual import paths inject.
 */
class ImportJobConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(ImportJobConfiguration.class)
            .withBean(ResourceUploadService.class, () -> mock(ResourceUploadService.class))
            .withBean(ResourceZipService.class, () -> mock(ResourceZipService.class))
            .withBean(ResourceRepositoryLoaderService.class, () -> mock(ResourceRepositoryLoaderService.class))
            .withBean(ImportRunRegistry.class, () -> mock(ImportRunRegistry.class))
            .withBean(ImportResourceRunService.class, () -> mock(ImportResourceRunService.class))
            .withBean(ImportResourceRepository.class, () -> mock(ImportResourceRepository.class))
            .withBean(ImportJobRepository.class, () -> mock(ImportJobRepository.class));

    @Test
    void wiresTheJobServiceAndKeepsTheApplicationTaskExecutor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ImportJobService.class);
            assertThat(context).hasSingleBean(SubmitImportJobCommandHandler.class);
            assertThat(context).hasSingleBean(ExecutorImportJobDispatcher.class);
            assertThat(context.getBeansOfType(Executor.class)).containsOnlyKeys("applicationTaskExecutor");
            assertThat(context.getBean(ImportJobSettings.class))
                    .isEqualTo(new ImportJobSettings(Duration.ofSeconds(10), Duration.ofHours(2)));
            ImportJobRepository jobRepository = context.getBean(ImportJobRepository.class);
            verify(jobRepository).findByStatusIn(Set.of(ImportJobStatus.STORING, ImportJobStatus.IMPORTING));
            verify(jobRepository).findByStatusIn(Set.of(ImportJobStatus.QUEUED));
        });
    }

    @Test
    void bindsTheBusyWaitDurations() {
        contextRunner
                .withPropertyValues("tt.league.import.jobs.busy-retry-interval=PT5S",
                        "tt.league.import.jobs.busy-timeout=PT30M")
                .run(context -> assertThat(context.getBean(ImportJobSettings.class))
                        .isEqualTo(new ImportJobSettings(Duration.ofSeconds(5), Duration.ofMinutes(30))));
    }

    @Test
    void failsToStartWithANonPositiveDuration() {
        contextRunner
                .withPropertyValues("tt.league.import.jobs.busy-timeout=PT0S")
                .run(context -> assertThat(context).hasFailed());
    }
}
