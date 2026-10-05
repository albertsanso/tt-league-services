package org.cttelsamicsterrassa.data.pipeline.runtime.polling;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.polling.AdaptivePollingTick;
import org.cttelsamicsterrassa.data.pipeline.core.polling.MatchDayRefresh;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettingsProvider;
import org.cttelsamicsterrassa.data.pipeline.core.polling.TrackerOpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.IngestStatusGateway;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollPolicyRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollScheduleRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollingAlerts;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.BcnesaCompetitionNames;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuilder;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayActions;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.OpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpIngestStatusGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the adaptive polling. The {@code OPEN_MATCH_DAYS} resolver is provided for every source, polled adaptively or
 * not; the tick (and so the season and zone) exist only when at least one source is polled.
 */
@Configuration(proxyBeanMethods = false)
public class PollingConfiguration {

    @Bean
    IngestStatusGateway ingestStatusGateway(@Qualifier("ingestRestClient") RestClient client, ObjectMapper json) {
        return new HttpIngestStatusGateway(client, json);
    }

    @Bean
    ScopeBuilder scopeBuilder(PipelineOrchestratorProperties.Polling polling) {
        return new ScopeBuilder(new BcnesaCompetitionNames(polling.bcnesaCompetitionNames()));
    }

    @Bean
    OpenMatchDayScopeResolver openMatchDayScopeResolver(
            MatchDayRepository matchDays, IngestStatusGateway status, ScopeBuilder scopeBuilder) {
        return new TrackerOpenMatchDayScopeResolver(matchDays, status, scopeBuilder);
    }

    @Bean
    MatchDayRefresh matchDayRefresh(
            MatchDayRepository matchDays,
            IngestStatusGateway status,
            ScopeBuilder scopeBuilder,
            TriggerRun triggerRun,
            MatchDayActions actions) {
        return new MatchDayRefresh(matchDays, status, scopeBuilder, triggerRun, actions);
    }

    @Bean
    PollingSettingsProvider pollingSettingsProvider(
            PollPolicyRepository policies, PipelineOrchestratorProperties.Polling polling) {
        return new PollingSettingsProvider(policies, polling.defaultSettings());
    }

    @Bean
    PollingAlerts pollingAlerts() {
        return new LoggingPollingAlerts();
    }

    @Bean
    AdaptivePollingTrigger adaptivePollingTrigger(
            PipelineOrchestratorProperties.Polling polling,
            PipelineOrchestratorProperties.Schedule schedule,
            MatchDayRepository matchDays,
            IngestStatusGateway status,
            PollScheduleRepository schedules,
            PollingSettingsProvider settings,
            TriggerRun triggerRun,
            PipelineRunRepository runs,
            PollingAlerts alerts,
            ScopeBuilder scopeBuilder,
            RunClock clock,
            LockingTaskExecutor schedulerLockingTaskExecutor) {
        AdaptivePollingTick tick = polling.sources().isEmpty() ? null
                : new AdaptivePollingTick(matchDays, status, schedules, settings, triggerRun, runs, alerts,
                        scopeBuilder, clock, schedule.season(), schedule.zoneId());
        return new AdaptivePollingTrigger(polling, source -> {
            if (tick == null) {
                throw new IllegalStateException("Adaptive polling is not configured");
            }
            tick.tick(source);
        }, schedulerLockingTaskExecutor);
    }
}
