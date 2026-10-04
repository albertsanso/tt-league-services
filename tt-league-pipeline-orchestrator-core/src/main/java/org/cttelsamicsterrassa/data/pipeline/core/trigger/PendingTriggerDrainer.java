package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Launches the source's pending trigger once a run of that source reaches a terminal status. Takes a supplier so the
 * runtime can break the bean cycle {@code TriggerRun -> RunLauncher -> RunObserver -> drainer -> TriggerRun}.
 */
public final class PendingTriggerDrainer implements RunObserver {

    private final Supplier<TriggerRun> triggerRun;

    public PendingTriggerDrainer(Supplier<TriggerRun> triggerRun) {
        this.triggerRun = Objects.requireNonNull(triggerRun, "triggerRun is required");
    }

    @Override
    public void runChanged(PipelineRun run) {
        if (run.status().isTerminal()) {
            triggerRun.get().launchPending(run.source());
        }
    }
}
