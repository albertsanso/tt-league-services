package org.cttelsamicsterrassa.data.pipeline.runtime.artifact;

import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.retention.ArtifactCleanup;
import org.cttelsamicsterrassa.data.pipeline.core.retention.ArtifactRetentionRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the artifact cleanup only when {@code tt.pipeline.retention} is configured. Without it artifacts are kept
 * forever and no job exists.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "tt.pipeline.retention", name = "cron")
public class ArtifactRetentionConfiguration {

    @Bean
    ArtifactCleanup artifactCleanup(
            ArtifactRetentionRepository retention,
            RunArtifactRepository artifactRows,
            ArtifactStore store,
            PipelineOrchestratorProperties properties,
            RunClock clock) {
        return new ArtifactCleanup(retention, artifactRows, store, properties.retention().toPolicy(), clock);
    }

    @Bean
    ArtifactCleanupSchedule artifactCleanupSchedule(
            PipelineOrchestratorProperties properties,
            ArtifactCleanup cleanup,
            LockingTaskExecutor schedulerLockingTaskExecutor) {
        return new ArtifactCleanupSchedule(properties.retention(), cleanup, schedulerLockingTaskExecutor);
    }
}
