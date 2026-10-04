package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import org.cttelsamicsterrassa.data.pipeline.core.execution.RunExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunDispatcher;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs {@link RunExecutor#execute} on a private fixed pool of platform threads. The pool is deliberately not a Spring
 * {@code Executor} bean: Boot's {@code applicationTaskExecutor} backs off when one exists.
 */
public final class ExecutorRunDispatcher implements RunDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutorRunDispatcher.class);
    private static final long SHUTDOWN_WAIT_SECONDS = 30;

    private final RunExecutor executor;
    private final ExecutorService pool;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public ExecutorRunDispatcher(RunExecutor executor, int maxConcurrentRuns) {
        this.executor = executor;
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threads = task -> {
            Thread thread = new Thread(task, "pipeline-run-" + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        this.pool = Executors.newFixedThreadPool(maxConcurrentRuns, threads);
    }

    /** A run that is already executing in this JVM is ignored. */
    @Override
    public void dispatch(UUID runId) {
        if (!inFlight.add(runId)) {
            LOG.info("run {} is already executing; ignoring the dispatch", runId);
            return;
        }
        try {
            pool.execute(() -> runOne(runId));
        } catch (RuntimeException e) {
            inFlight.remove(runId);
            throw e;
        }
    }

    private void runOne(UUID runId) {
        try {
            executor.execute(runId);
        } catch (RuntimeException e) {
            LOG.error("run {} escaped the executor", runId, e);
        } finally {
            inFlight.remove(runId);
        }
    }

    /** Interrupts running executions, which stay active in the database and are resumed by recovery. */
    @PreDestroy
    public void shutdown() throws InterruptedException {
        pool.shutdownNow();
        if (!pool.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
            LOG.warn("run executions did not stop within {} seconds", SHUTDOWN_WAIT_SECONDS);
        }
    }
}
