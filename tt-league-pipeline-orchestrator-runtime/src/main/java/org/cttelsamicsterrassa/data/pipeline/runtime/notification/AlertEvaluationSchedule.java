package org.cttelsamicsterrassa.data.pipeline.runtime.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Periodic alert evaluation: a fixed-delay tick, the first one after one interval, that only requests an evaluation.
 * There is no ShedLock: the partial unique index on the alerts makes raising idempotent across instances. It starts
 * only when notifications are enabled. The single-thread scheduler is owned here and is deliberately not a bean (a
 * {@code TaskScheduler} bean is an {@code Executor}).
 */
public final class AlertEvaluationSchedule implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(AlertEvaluationSchedule.class);

    private final boolean enabled;
    private final Duration interval;
    private final AlertRequests requests;
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> future;
    private boolean running;

    public AlertEvaluationSchedule(boolean enabled, Duration interval, AlertRequests requests) {
        this.enabled = enabled;
        this.interval = Objects.requireNonNull(interval, "interval is required");
        this.requests = Objects.requireNonNull(requests, "requests is required");
    }

    /** Starts after the dispatcher and stops before it. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public synchronized void start() {
        if (running || !enabled) {
            return;
        }
        running = true;
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("pipeline-alerts-schedule-");
        scheduler.setDaemon(true);
        scheduler.setErrorHandler(failure -> LOG.error("alert evaluation tick failed", failure));
        scheduler.initialize();
        future = scheduler.scheduleWithFixedDelay(requests::request, Instant.now().plus(interval), interval);
        LOG.info("periodic alert evaluation every {}", interval);
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
}
