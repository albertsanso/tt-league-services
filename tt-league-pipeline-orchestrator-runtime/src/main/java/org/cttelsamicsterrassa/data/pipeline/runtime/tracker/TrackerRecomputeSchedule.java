package org.cttelsamicsterrassa.data.pipeline.runtime.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.SourceSeason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.RecomputeRequests;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import java.util.LinkedHashSet;
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

/**
 * Periodic recompute: a fixed-delay tick, taken under the ShedLock lock {@code pipeline-tracker-recompute} so only one
 * orchestrator instance enqueues it, requests a recompute without a run for every source and season with unclosed
 * match days plus the fixed-schedule season of each scheduled source. With nothing tracked and no schedule it does
 * nothing: the first run of a source bootstraps its tracking. The single-thread scheduler is owned here and is
 * deliberately not a bean (a {@code TaskScheduler} bean is an {@code Executor}).
 */
public final class TrackerRecomputeSchedule implements SmartLifecycle {

    static final String LOCK_NAME = "pipeline-tracker-recompute";

    private static final Logger LOG = LoggerFactory.getLogger(TrackerRecomputeSchedule.class);

    private final PipelineOrchestratorProperties.Tracker settings;
    private final PipelineOrchestratorProperties.Schedule schedule;
    private final MatchDayRepository repository;
    private final RecomputeRequests requests;
    private final LockingTaskExecutor locks;
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> future;
    private boolean running;

    public TrackerRecomputeSchedule(
            PipelineOrchestratorProperties.Tracker settings,
            PipelineOrchestratorProperties.Schedule schedule,
            MatchDayRepository repository,
            RecomputeRequests requests,
            LockingTaskExecutor locks) {
        this.settings = Objects.requireNonNull(settings, "settings is required");
        this.schedule = Objects.requireNonNull(schedule, "schedule is required");
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.requests = Objects.requireNonNull(requests, "requests is required");
        this.locks = Objects.requireNonNull(locks, "locks is required");
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("pipeline-tracker-");
        scheduler.setDaemon(true);
        scheduler.setErrorHandler(failure -> LOG.error("tracker recompute tick failed", failure));
        scheduler.initialize();
        future = scheduler.scheduleWithFixedDelay(this::runTick, settings.recomputeInterval());
        LOG.info("periodic tracker recompute every {}", settings.recomputeInterval());
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

    /** One tick; does nothing when another instance holds the lock. */
    void runTick() {
        LockConfiguration lock = new LockConfiguration(
                ClockProvider.now(), LOCK_NAME, settings.lockAtMostFor(), settings.lockAtLeastFor());
        locks.executeWithLock((Runnable) this::enqueueTargets, lock);
    }

    private void enqueueTargets() {
        for (SourceSeason target : targets()) {
            requests.request(target.source(), target.season(), null);
        }
    }

    Set<SourceSeason> targets() {
        Set<SourceSeason> targets = new LinkedHashSet<>(repository.findSourceSeasonsWithUnclosedDays());
        if (!schedule.scheduledSources().isEmpty()) {
            for (PipelineSource source : schedule.scheduledSources()) {
                targets.add(new SourceSeason(source, schedule.season()));
            }
        }
        return targets;
    }
}
