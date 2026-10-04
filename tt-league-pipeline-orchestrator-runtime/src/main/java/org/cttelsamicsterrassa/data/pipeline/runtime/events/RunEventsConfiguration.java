package org.cttelsamicsterrassa.data.pipeline.runtime.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.pipeline.runtime.api.RunDtoMapper;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RunEventsConfiguration {

    @Bean(destroyMethod = "shutdown")
    RunEventBroadcaster runEventBroadcaster(
            RunDtoMapper mapper, ObjectMapper json, PipelineOrchestratorProperties.Events settings) {
        return new RunEventBroadcaster(mapper, json, settings);
    }
}
