package org.cttelsamicsterrassa.data.pipeline.runtime.polling;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Fires the adaptive polling tick for every polled source at a fixed delay. Each source's tick runs under the ShedLock
 * lock {@code pipeline-polling-<SOURCE>}, so only one orchestrator instance polls a source; a failure of one source is
 * logged and never stops the others or later ticks. The single-thread scheduler is owned here and is deliberately not
 * a bean: a {@code TaskScheduler} bean is an {@code Executor}.
 */
public final class AdaptivePollingTrigger implements SmartLifecycle {

    static final String LOCK_PREFIX = "pipeline-polling-";

    private static final Logger LOG = LoggerFactory.getLogger(AdaptivePollingTrigger.class);

    private final PipelineOrchestratorProperties.Polling settings;
    private final Consumer<PipelineSource> tick;
    private final LockingTaskExecutor locks;
    private final Set<PipelineSource> sources;
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> future;
    private boolean running;

    public AdaptivePollingTrigger(
            PipelineOrchestratorProperties.Polling settings, Consumer<PipelineSource> tick, LockingTaskExecutor locks) {
        this.settings = Objects.requireNonNull(settings, "settings is required");
        this.tick = Objects.requireNonNull(tick, "tick is required");
        this.locks = Objects.requireNonNull(locks, "locks is required");
        this.sources = settings.sources().isEmpty()
                ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(settings.sources()));
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        if (sources.isEmpty()) {
            LOG.info("no adaptively polled source configured; adaptive polling is disabled");
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("pipeline-polling-");
        scheduler.setDaemon(true);
        scheduler.setErrorHandler(failure -> LOG.error("adaptive polling tick failed", failure));
        scheduler.initialize();
        future = scheduler.scheduleWithFixedDelay(this::runTick, settings.tickInterval());
        LOG.info("adaptive polling for {} every {}", sources, settings.tickInterval());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        if (future != null) {
            future.cancel(false);
            future = null;
        }
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

    /** One pass over every polled source, in source order; a failing source does not stop the next one. */
    void runTick() {
        for (PipelineSource source : PipelineSource.values()) {
            if (sources.contains(source)) {
                try {
                    runTick(source);
                } catch (RuntimeException failure) {
                    LOG.error("adaptive polling tick for {} failed", source, failure);
                }
            }
        }
    }

    /** One tick for {@code source}; does nothing when another instance holds the source's lock. */
    void runTick(PipelineSource source) {
        if (!sources.contains(source)) {
            throw new IllegalStateException("Source " + source + " is not polled adaptively");
        }
        LockConfiguration lock = new LockConfiguration(ClockProvider.now(), LOCK_PREFIX + source,
                settings.lockAtMostFor(), settings.lockAtLeastFor());
        locks.executeWithLock((Runnable) () -> tick.accept(source), lock);
    }

    Set<PipelineSource> sources() {
        return sources;
    }

    synchronized boolean schedulerActive() {
        return scheduler != null;
    }
}
