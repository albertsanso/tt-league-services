package org.cttelsamicsterrassa.data.pipeline.runtime.statistics;

import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DailyStatsAggregator;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsQueries;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsSettings;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.DailyStatsRepository;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.StatisticsReadRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the statistics. {@link DailyStatsAggregator} is the only writer of the daily snapshots and
 * {@link StatisticsQueries} is read-only; the daily job takes its lock through the shared {@link LockingTaskExecutor}.
 */
@Configuration(proxyBeanMethods = false)
public class StatisticsConfiguration {

    @Bean
    DailyStatsAggregator dailyStatsAggregator(
            StatisticsReadRepository reads, DailyStatsRepository stored, RunClock clock,
            StatisticsSettings settings) {
        return new DailyStatsAggregator(reads, stored, clock, settings);
    }

    @Bean
    StatisticsQueries statisticsQueries(
            StatisticsReadRepository reads,
            DailyStatsRepository stored,
            MatchDayRepository matchDays,
            RunClock clock,
            StatisticsSettings settings) {
        return new StatisticsQueries(reads, stored, matchDays, clock, settings);
    }

    @Bean
    DailyStatsSchedule dailyStatsSchedule(
            StatisticsSettings settings, DailyStatsAggregator aggregator, LockingTaskExecutor schedulerLockingTaskExecutor) {
        return new DailyStatsSchedule(settings, aggregator, schedulerLockingTaskExecutor);
    }
}
