package org.cttelsamicsterrassa.data.pipeline.runtime.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayActions;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayTracker;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackerRunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformMatchGateway;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpPlatformMatchGateway;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the match-day tracker. Recomputes run only through {@link TrackerRecomputeDispatcher}; the periodic schedule
 * takes its lock through the shared {@link LockingTaskExecutor}.
 */
@Configuration(proxyBeanMethods = false)
public class TrackerConfiguration {

    @Bean
    PlatformMatchGateway platformMatchGateway(
            @Qualifier("platformRestClient") RestClient client, ObjectMapper json) {
        return new HttpPlatformMatchGateway(client, json);
    }

    @Bean
    MatchDayTracker matchDayTracker(
            PlatformMatchGateway gateway, MatchDayRepository repository, RunClock clock) {
        return new MatchDayTracker(gateway, repository, clock);
    }

    @Bean
    MatchDayActions matchDayActions(MatchDayRepository repository, RunClock clock) {
        return new MatchDayActions(repository, clock);
    }

    @Bean
    TrackerRecomputeDispatcher trackerRecomputeDispatcher(MatchDayTracker tracker) {
        return new TrackerRecomputeDispatcher(tracker);
    }

    @Bean
    TrackerRunObserver trackerRunObserver(TrackerRecomputeDispatcher dispatcher) {
        return new TrackerRunObserver(dispatcher);
    }

    @Bean
    TrackerRecomputeSchedule trackerRecomputeSchedule(
            PipelineOrchestratorProperties.Tracker tracker,
            PipelineOrchestratorProperties.Schedule schedule,
            MatchDayRepository repository,
            TrackerRecomputeDispatcher dispatcher,
            LockingTaskExecutor schedulerLockingTaskExecutor) {
        return new TrackerRecomputeSchedule(tracker, schedule, repository, dispatcher, schedulerLockingTaskExecutor);
    }
}
