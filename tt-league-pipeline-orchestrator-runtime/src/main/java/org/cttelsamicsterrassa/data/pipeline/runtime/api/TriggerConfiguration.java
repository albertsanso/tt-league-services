package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.OpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerEvents;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the shared trigger path from the core use case and the persistence adapters. */
@Configuration(proxyBeanMethods = false)
public class TriggerConfiguration {

    /** Replaced by the real resolver when the match-day tracker and scope builder provide their own bean. */
    @Bean
    @ConditionalOnMissingBean(OpenMatchDayScopeResolver.class)
    OpenMatchDayScopeResolver openMatchDayScopeResolver() {
        return new UnavailableOpenMatchDayScopeResolver();
    }

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
}
