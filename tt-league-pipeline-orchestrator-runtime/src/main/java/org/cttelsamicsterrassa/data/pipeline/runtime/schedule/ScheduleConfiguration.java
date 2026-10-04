package org.cttelsamicsterrassa.data.pipeline.runtime.schedule;

import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the fixed-schedule trigger. No {@code @EnableScheduling} or {@code @EnableSchedulerLock}: the scheduler is
 * private to {@link ScheduledRunTrigger} and the lock is taken explicitly through {@link LockingTaskExecutor}.
 */
@Configuration(proxyBeanMethods = false)
public class ScheduleConfiguration {

    static final String LOCK_TABLE = "pipeline.shedlock";

    /** Lock times come from the database clock, so every instance compares the same clock. */
    @Bean
    LockProvider schedulerLockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName(LOCK_TABLE)
                .usingDbTime()
                .build());
    }

    @Bean
    LockingTaskExecutor schedulerLockingTaskExecutor(LockProvider schedulerLockProvider) {
        return new DefaultLockingTaskExecutor(schedulerLockProvider);
    }

    @Bean
    ScheduledRunTrigger scheduledRunTrigger(
            PipelineOrchestratorProperties.Schedule schedule,
            TriggerRun triggerRun,
            LockingTaskExecutor schedulerLockingTaskExecutor) {
        return new ScheduledRunTrigger(schedule, triggerRun, schedulerLockingTaskExecutor);
    }
}
