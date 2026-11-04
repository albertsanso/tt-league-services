package org.cttelsamicsterrassa.data.pipeline.runtime.statistics;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.AggregationOutcome;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DailyStatsAggregator;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Daily statistics job: every day at {@code dailyAt} in the statistics zone, plus one catch-up shortly after start,
 * it runs {@link DailyStatsAggregator#catchUp()} under the ShedLock lock {@code pipeline-daily-stats}, so only one
 * orchestrator instance aggregates. A failure is logged and never stops the schedule: the next tick catches up
 * again. The single-thread scheduler is owned here and is deliberately not a bean (a {@code TaskScheduler} bean is an
 * {@code Executor}).
 */
public final class DailyStatsSchedule implements SmartLifecycle {

    static final String LOCK_NAME = "pipeline-daily-stats";
    static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(10);
    static final Duration LOCK_AT_LEAST_FOR = Duration.ofSeconds(30);
    static final Duration STARTUP_CATCH_UP_DELAY = Duration.ofMinutes(1);

    private static final Logger LOG = LoggerFactory.getLogger(DailyStatsSchedule.class);

    private final StatisticsSettings settings;
    private final DailyStatsAggregator aggregator;
    private final LockingTaskExecutor locks;
    private final Duration startupCatchUpDelay;
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> daily;
    private ScheduledFuture<?> startup;
    private boolean running;

    public DailyStatsSchedule(
            StatisticsSettings settings, DailyStatsAggregator aggregator, LockingTaskExecutor locks) {
        this(settings, aggregator, locks, STARTUP_CATCH_UP_DELAY);
    }

    DailyStatsSchedule(
            StatisticsSettings settings,
            DailyStatsAggregator aggregator,
            LockingTaskExecutor locks,
            Duration startupCatchUpDelay) {
        this.settings = Objects.requireNonNull(settings, "settings is required");
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator is required");
        this.locks = Objects.requireNonNull(locks, "locks is required");
        this.startupCatchUpDelay = Objects.requireNonNull(startupCatchUpDelay, "startupCatchUpDelay is required");
    }

    /** {@code dailyAt} (seconds ignored) in the statistics zone as a Spring six-field cron. */
    CronTrigger trigger() {
        LocalTime at = settings.dailyAt();
        return new CronTrigger("0 " + at.getMinute() + " " + at.getHour() + " * * *", settings.zone());
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("pipeline-stats-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        daily = scheduler.schedule(this::runTick, trigger());
        startup = scheduler.schedule(this::runTick, Instant.now().plus(startupCatchUpDelay));
        LOG.info("daily statistics at {} in {}, first catch-up in {}", settings.dailyAt(), settings.zone(),
                startupCatchUpDelay);
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        for (ScheduledFuture<?> future : new ScheduledFuture<?>[] {daily, startup}) {
            if (future != null) {
                future.cancel(false);
            }
        }
        daily = null;
        startup = null;
        scheduler.shutdown();
        scheduler = null;
        running = false;
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    /** One tick; does nothing when another instance holds the lock. A failure is logged and the next tick retries. */
    void runTick() {
        try {
            LockConfiguration lock = new LockConfiguration(
                    ClockProvider.now(), LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
            locks.executeWithLock((Runnable) this::catchUp, lock);
        } catch (RuntimeException e) {
            LOG.warn("daily statistics catch-up failed; the next tick retries", e);
        }
    }

    private void catchUp() {
        AggregationOutcome outcome = aggregator.catchUp();
        if (!outcome.isEmpty()) {
            LOG.info("daily statistics aggregated {} day(s): {}", outcome.days().size(), outcome.days());
        }
    }
}
