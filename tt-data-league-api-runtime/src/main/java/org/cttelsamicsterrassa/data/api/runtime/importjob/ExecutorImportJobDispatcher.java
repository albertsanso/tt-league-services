package org.cttelsamicsterrassa.data.api.runtime.importjob;

import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs import jobs one at a time on a private single-thread executor, in dispatch order.
 *
 * <p>The executor is deliberately not exposed as an {@code Executor} bean: Spring Boot backs off its
 * {@code applicationTaskExecutor} as soon as any {@code Executor} bean exists, and the manual upload and start
 * paths inject the plain {@code Executor}. The thread is a daemon and shutdown interrupts it; a job still waiting
 * to start stays {@code QUEUED} and an in-flight one is failed by the restart recovery.</p>
 */
public class ExecutorImportJobDispatcher implements ImportJobDispatcher, DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorImportJobDispatcher.class);
    static final String THREAD_NAME = "import-job-1";

    private final ExecutorService executor;
    private final Consumer<UUID> jobRunner;

    public ExecutorImportJobDispatcher(Consumer<UUID> jobRunner) {
        this(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        }), jobRunner);
    }

    ExecutorImportJobDispatcher(ExecutorService executor, Consumer<UUID> jobRunner) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.jobRunner = Objects.requireNonNull(jobRunner, "jobRunner");
    }

    /**
     * @throws java.util.concurrent.RejectedExecutionException when the dispatcher has been shut down
     */
    @Override
    public void dispatch(UUID jobId) {
        executor.execute(() -> {
            try {
                jobRunner.accept(jobId);
            } catch (RuntimeException exception) {
                LOGGER.error("Import job {} could not be executed", jobId, exception);
            }
        });
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
