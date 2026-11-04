package org.cttelsamicsterrassa.data.pipeline.runtime.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;
import org.cttelsamicsterrassa.data.pipeline.runtime.api.RunDtoMapper;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.notification.AlertDispatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class RunEventsConfiguration {

    @Bean(destroyMethod = "shutdown")
    RunEventBroadcaster runEventBroadcaster(
            RunDtoMapper mapper, RunUnitRepository units, ImportReportRepository reports, ObjectMapper json,
            PipelineOrchestratorProperties.Events settings) {
        return new RunEventBroadcaster(mapper, units, reports, json, settings);
    }

    /**
     * The listener the tracker and the controllers see: the live views first, then an alert evaluation request. The
     * broadcaster is injected by concrete type, so this primary bean is the only {@link MatchDayChangeListener} they
     * resolve.
     */
    @Bean
    @Primary
    MatchDayChangeListener matchDayChangeListener(RunEventBroadcaster broadcaster, AlertDispatcher alerts) {
        return new CompositeMatchDayChangeListener(broadcaster, alerts);
    }
}
