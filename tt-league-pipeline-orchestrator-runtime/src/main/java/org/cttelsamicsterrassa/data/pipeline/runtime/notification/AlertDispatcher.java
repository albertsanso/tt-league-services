package org.cttelsamicsterrassa.data.pipeline.runtime.notification;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertEvaluator;
import org.cttelsamicsterrassa.data.pipeline.core.alert.EvaluationOutcome;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRequests;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notification;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.NotificationException;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessException;

/**
 * The only way an alert evaluation or a notification runs. One private single-thread executor (deliberately not an
 * {@code Executor} bean) handles the work in order, so no run, tracker, polling or request thread ever waits for SMTP.
 * Evaluation requests are coalesced: at most one pass is queued. A failed pass writes nothing more than the evaluator
 * already did; it is logged and dropped because the next trigger repairs the state. When notifications are disabled
 * the dispatcher has neither evaluator nor notifier and every call is a no-op.
 */
public final class AlertDispatcher implements AlertRequests, SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(AlertDispatcher.class);
    private static final long STOP_WAIT_SECONDS = 10;

    private final AlertEvaluator evaluator;
    private final Notifier notifier;
    private final AtomicBoolean pending = new AtomicBoolean();
    private ExecutorService executor;
    private boolean running;

    public AlertDispatcher(AlertEvaluator evaluator, Notifier notifier) {
        this.evaluator = evaluator;
        this.notifier = notifier;
    }

    /** A dispatcher for disabled notifications: {@link #request()} and {@link #send} do nothing. */
    public static AlertDispatcher disabled() {
        return new AlertDispatcher(null, null);
    }

    public boolean enabled() {
        return evaluator != null && notifier != null;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        if (!enabled()) {
            LOG.info("notifications disabled (PIPELINE_MAIL_HOST is not set)");
            return;
        }
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "pipeline-alerts");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (executor == null) {
            return;
        }
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

    /** Enqueues one evaluation pass unless one is already queued; never throws. */
    @Override
    public synchronized void request() {
        if (!enabled()) {
            return;
        }
        if (!running) {
            LOG.warn("alert evaluation rejected: the dispatcher is stopped");
            return;
        }
        if (!pending.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::evaluate);
        } catch (RejectedExecutionException e) {
            pending.set(false);
            LOG.warn("alert evaluation rejected: {}", e.getMessage());
        }
    }

    /** Enqueues a direct notification (the polling alerts); never throws. */
    public synchronized void send(Notification notification) {
        if (!enabled()) {
            return;
        }
        if (!running) {
            LOG.warn("notification '{}' rejected: the dispatcher is stopped", notification.subject());
            return;
        }
        try {
            executor.execute(() -> deliver(notification));
        } catch (RejectedExecutionException e) {
            LOG.warn("notification '{}' rejected: {}", notification.subject(), e.getMessage());
        }
    }

    private void evaluate() {
        // Cleared first: a request that arrives while this pass runs queues the next pass.
        pending.set(false);
        try {
            EvaluationOutcome outcome = evaluator.evaluate();
            if (!outcome.isEmpty()) {
                LOG.info("alert evaluation: raised {}, cleared {}, sent {}, failed {}", outcome.raised(),
                        outcome.cleared(), outcome.sent(), outcome.failed());
            }
        } catch (DataAccessException e) {
            LOG.warn("alert evaluation dropped: {}", e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            // Task boundary of the single worker thread: an unexpected failure must not stop later passes.
            LOG.error("alert evaluation failed unexpectedly", e);
        }
    }

    private void deliver(Notification notification) {
        try {
            notifier.send(notification);
        } catch (NotificationException e) {
            LOG.warn("notification '{}' not sent: {}", notification.subject(), e.getMessage());
        } catch (RuntimeException e) {
            // Task boundary of the single worker thread: an unexpected failure must not stop later work.
            LOG.error("notification '{}' failed unexpectedly", notification.subject(), e);
        }
    }

    /** Blocks until every task accepted so far has finished. Tests only. */
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
