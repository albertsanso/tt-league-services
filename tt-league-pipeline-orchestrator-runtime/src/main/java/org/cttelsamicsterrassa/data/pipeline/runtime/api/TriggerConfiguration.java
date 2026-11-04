package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ReplayRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.OpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerEvents;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the shared trigger path from the core use case and the persistence adapters. The open-match-day resolver is
 * the tracker-backed one from the polling configuration.
 */
@Configuration(proxyBeanMethods = false)
public class TriggerConfiguration {

    @Bean
    TriggerRun triggerRun(
            PipelineRunRepository runs,
            PendingTriggerRepository pendingTriggers,
            OpenMatchDayScopeResolver openMatchDays,
            RunLauncher launcher,
            PendingTriggerEvents events,
            RunClock clock) {
        return new TriggerRun(runs, pendingTriggers, openMatchDays, launcher, events, clock);
    }

    /** The only creator of RETRY runs; it launches through the same {@link RunLauncher}. */
    @Bean
    ReplayRun replayRun(
            PipelineRunRepository runs, RunArtifactRepository artifactRows, ArtifactStore artifacts,
            RunLauncher launcher) {
        return new ReplayRun(runs, artifactRows, artifacts, launcher);
    }
}
