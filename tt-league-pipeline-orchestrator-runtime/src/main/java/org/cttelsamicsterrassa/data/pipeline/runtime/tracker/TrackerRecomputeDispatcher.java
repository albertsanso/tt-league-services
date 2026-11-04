package org.cttelsamicsterrassa.data.pipeline.runtime.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayTracker;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.RecomputeOutcome;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.RunRef;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackerInconsistencyException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.RecomputeRequests;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.StaleMatchDayException;
import org.cttelsamicsterrassa.data.pipeline.runtime.events.MatchDayChangeListener;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * The only way a recompute runs. One private single-thread executor (deliberately not an {@code Executor} bean)
 * handles requests in order, so recomputes never overlap in this instance. A failed recompute writes nothing; it is
 * logged and dropped, because the next trigger repairs the state.
 */
public final class TrackerRecomputeDispatcher implements RecomputeRequests, SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(TrackerRecomputeDispatcher.class);
    private static final long STOP_WAIT_SECONDS = 10;

    private final MatchDayTracker tracker;
    private final MatchDayChangeListener changes;
    private ExecutorService executor;
    private boolean running;

    public TrackerRecomputeDispatcher(MatchDayTracker tracker) {
        this(tracker, (source, season, matchDayId, cause) -> { });
    }

    /** {@code changes} is told after every recompute that changed something; its failures are logged only. */
    public TrackerRecomputeDispatcher(MatchDayTracker tracker, MatchDayChangeListener changes) {
        this.tracker = Objects.requireNonNull(tracker, "tracker is required");
        this.changes = Objects.requireNonNull(changes, "changes is required");
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "tracker-recompute");
            thread.setDaemon(true);
            return thread;
        });
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(STOP_WAIT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    /** Enqueues a recompute and returns at once; never throws. A request after {@link #stop()} is logged and dropped. */
    @Override
    public synchronized void request(PipelineSource source, String season, RunRef run) {
        if (!running) {
            LOG.warn("tracker recompute for {} {} (run {}) rejected: the dispatcher is stopped", source, season,
                    run == null ? "none" : run.runId());
            return;
        }
        try {
            executor.execute(() -> recompute(source, season, run));
        } catch (RejectedExecutionException e) {
            LOG.warn("tracker recompute for {} {} (run {}) rejected: {}", source, season,
                    run == null ? "none" : run.runId(), e.getMessage());
        }
    }

    private void recompute(PipelineSource source, String season, RunRef run) {
        String runId = run == null ? "none" : run.runId().toString();
        try {
            RecomputeOutcome outcome = tracker.recompute(source, season, run);
            LOG.info("tracker recompute for {} {} (run {}): {}", source, season, runId, outcome);
            if (outcome.hasChanges()) {
                notifyChanges(source, season);
            }
        } catch (GatewayException | TrackerInconsistencyException | StaleMatchDayException e) {
            LOG.warn("tracker recompute for {} {} (run {}) dropped: {}: {}", source, season, runId,
                    e.getClass().getSimpleName(), e.getMessage());
        } catch (RuntimeException e) {
            // Task boundary of the single worker thread: an unexpected failure must not stop later recomputes.
            LOG.error("tracker recompute for {} {} (run {}) failed unexpectedly", source, season, runId, e);
        }
    }

    private void notifyChanges(PipelineSource source, String season) {
        try {
            changes.matchDaysChanged(source, season, null, MatchDayChangeListener.Cause.RECOMPUTED);
        } catch (RuntimeException e) {
            LOG.warn("match-day change listener failed for {} {}: {}", source, season, e.toString());
        }
    }

    /** Blocks until every request accepted so far has finished. Tests only. */
    void awaitIdle() throws InterruptedException, ExecutionException {
        ExecutorService current;
        synchronized (this) {
            current = executor;
        }
        if (current != null) {
            current.submit(() -> { }).get();
        }
    }
}
