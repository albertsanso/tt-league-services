package org.cttelsamicsterrassa.data.pipeline.runtime.schedule;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ScheduledRunTick;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Fires {@link ScheduledRunTick} for every source with a configured cron. Each tick runs under the ShedLock lock
 * {@code pipeline-schedule-<SOURCE>}, so only one orchestrator instance fires it. The single-thread scheduler is
 * owned here and is deliberately not a bean: a {@code TaskScheduler} bean is an {@code Executor}.
 */
public final class ScheduledRunTrigger implements SmartLifecycle {

    static final String LOCK_PREFIX = "pipeline-schedule-";

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledRunTrigger.class);

    private final PipelineOrchestratorProperties.Schedule schedule;
    private final LockingTaskExecutor locks;
    private final ScheduledRunTick tick;
    private final Map<PipelineSource, CronTrigger> triggers;
    private final Map<PipelineSource, ScheduledFuture<?>> futures = new EnumMap<>(PipelineSource.class);
    private ThreadPoolTaskScheduler scheduler;
    private boolean running;

    public ScheduledRunTrigger(
            PipelineOrchestratorProperties.Schedule schedule, TriggerRun triggerRun, LockingTaskExecutor locks) {
        this.schedule = Objects.requireNonNull(schedule, "schedule is required");
        Objects.requireNonNull(triggerRun, "triggerRun is required");
        this.locks = Objects.requireNonNull(locks, "locks is required");
        Map<PipelineSource, CronTrigger> bySource = new EnumMap<>(PipelineSource.class);
        for (PipelineSource source : schedule.scheduledSources()) {
            bySource.put(source, new CronTrigger(schedule.cron(source), schedule.zoneId()));
        }
        this.triggers = Collections.unmodifiableMap(bySource);
        this.tick = triggers.isEmpty() ? null : new ScheduledRunTick(triggerRun, schedule.season());
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        if (triggers.isEmpty()) {
            LOG.info("no source schedule configured; scheduled runs are disabled");
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("pipeline-schedule-");
        scheduler.setDaemon(true);
        scheduler.setErrorHandler(failure -> LOG.error("scheduled tick failed", failure));
        scheduler.initialize();
        triggers.forEach((source, trigger) -> {
            futures.put(source, scheduler.schedule(() -> runTick(source), trigger));
            LOG.info("scheduled full-season runs for {} at '{}' ({}), season {}", source, trigger.getExpression(),
                    schedule.zoneId(), schedule.season());
        });
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        futures.values().forEach(future -> future.cancel(false));
        futures.clear();
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
        running = false;
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    /** One tick for {@code source}; does nothing when another instance holds the source's lock. */
    void runTick(PipelineSource source) {
        if (tick == null || !triggers.containsKey(source)) {
            throw new IllegalStateException("Source " + source + " has no schedule");
        }
        LockConfiguration lock = new LockConfiguration(ClockProvider.now(), LOCK_PREFIX + source,
                schedule.lockAtMostFor(), schedule.lockAtLeastFor());
        locks.executeWithLock((Runnable) () -> tick.tick(source), lock);
    }

    Map<PipelineSource, CronTrigger> triggers() {
        return triggers;
    }

    synchronized Set<PipelineSource> registeredSources() {
        return Set.copyOf(futures.keySet());
    }

    synchronized boolean schedulerActive() {
        return scheduler != null;
    }
}
