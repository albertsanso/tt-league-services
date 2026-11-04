package org.cttelsamicsterrassa.data.pipeline.runtime.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.OperationalGauges;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsQueries;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the run meters and the operational gauges; meters are created only here and in the two classes below. */
@Configuration(proxyBeanMethods = false)
public class MetricsConfiguration {

    @Bean
    RunMetricsObserver runMetricsObserver(MeterRegistry registry, PipelineRunRepository runs) {
        return new RunMetricsObserver(registry, runs);
    }

    @Bean
    OperationalGauges operationalGauges(StatisticsQueries queries, MatchDayRepository matchDays) {
        return new OperationalGauges(queries, matchDays);
    }

    @Bean
    OperationalGaugeBinder operationalGaugeBinder(OperationalGauges gauges, RunClock clock) {
        return new OperationalGaugeBinder(gauges, clock);
    }
}
