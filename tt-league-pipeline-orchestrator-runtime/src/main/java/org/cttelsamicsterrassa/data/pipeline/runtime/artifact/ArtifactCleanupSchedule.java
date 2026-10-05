package org.cttelsamicsterrassa.data.pipeline.runtime.artifact;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.retention.ArtifactCleanup;
import org.cttelsamicsterrassa.data.pipeline.core.retention.CleanupOutcome;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Artifact retention job: on the configured cron and zone it runs {@link ArtifactCleanup#run()} under the ShedLock
 * lock {@code pipeline-artifact-cleanup}, so only one orchestrator instance purges. There is no catch-up at startup
 * and no run ever triggers it. A failure is logged and never stops the schedule: the next tick tries again. The
 * single-thread scheduler is owned here and is deliberately not a bean (a {@code TaskScheduler} bean is an
 * {@code Executor}).
 */
public final class ArtifactCleanupSchedule implements SmartLifecycle {

    static final String LOCK_NAME = "pipeline-artifact-cleanup";
    static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(30);
    static final Duration LOCK_AT_LEAST_FOR = Duration.ofSeconds(30);

    private static final Logger LOG = LoggerFactory.getLogger(ArtifactCleanupSchedule.class);

    private final PipelineOrchestratorProperties.Retention settings;
    private final ArtifactCleanup cleanup;
    private final LockingTaskExecutor locks;
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> future;
    private boolean running;

    public ArtifactCleanupSchedule(
            PipelineOrchestratorProperties.Retention settings, ArtifactCleanup cleanup, LockingTaskExecutor locks) {
        this.settings = Objects.requireNonNull(settings, "settings is required");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup is required");
        this.locks = Objects.requireNonNull(locks, "locks is required");
    }

    CronTrigger trigger() {
        return new CronTrigger(settings.cron(), settings.zoneId());
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("pipeline-retention-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        future = scheduler.schedule(this::runTick, trigger());
        LOG.info("artifact retention cleanup on '{}' in {}", settings.cron(), settings.zone());
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
            locks.executeWithLock((Runnable) this::purge, lock);
        } catch (RuntimeException e) {
            LOG.warn("artifact retention cleanup failed; the next tick retries", e);
        }
    }

    private void purge() {
        CleanupOutcome outcome = cleanup.run();
        if (!outcome.purgedKeys().isEmpty() || !outcome.failedKeys().isEmpty()) {
            LOG.info("artifact retention purged {} artifact(s) ({} bytes); {} failed: {}", outcome.purgedKeys().size(),
                    outcome.purgedBytes(), outcome.failedKeys().size(), outcome.failedKeys());
        }
    }
}
