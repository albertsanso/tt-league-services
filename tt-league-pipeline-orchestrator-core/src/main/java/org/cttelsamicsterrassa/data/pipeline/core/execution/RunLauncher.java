package org.cttelsamicsterrassa.data.pipeline.core.execution;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The single trigger path: queues a run and hands it to the dispatcher. */
public final class RunLauncher {

    /** What a caller asks for; {@code retryOfRunId} is set exactly for RETRY runs. */
    public record LaunchRequest(
            PipelineSource source,
            String season,
            RunScope scope,
            RunTrigger trigger,
            String requestedBy,
            UUID retryOfRunId) {
    }

    private final PipelineRunRepository runs;
    private final RunDispatcher dispatcher;
    private final RunClock clock;

    public RunLauncher(PipelineRunRepository runs, RunDispatcher dispatcher, RunClock clock) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Creates the queued run and dispatches it. {@code ActiveRunConflictException} propagates unchanged. When the
     * dispatch fails, the queued run is failed with DISPATCH_FAILED and the exception is rethrown.
     */
    public PipelineRun launch(LaunchRequest request) {
        Instant now = clock.now();
        PipelineRun queued = PipelineRun.queue(UUID.randomUUID(), request.source(), request.season(),
                request.scope(), request.trigger(), request.requestedBy(), request.retryOfRunId(), now);
        PipelineRun created = runs.create(queued);
        try {
            dispatcher.dispatch(created.id());
        } catch (RuntimeException e) {
            String message = FailureCode.DISPATCH_FAILED + ": " + e.getClass().getName() + ": " + e.getMessage();
            try {
                runs.update(created.fail(new RunError(FailureCode.DISPATCH_FAILED.name(), message), clock.now()));
            } catch (RuntimeException failure) {
                e.addSuppressed(failure);
            }
            throw e;
        }
        return created;
    }
}
