package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import java.util.List;
import java.util.Objects;

/**
 * Fans a change out to several observers in order. Observers are side channels and must never fail a run, so a
 * {@link RuntimeException} from one is logged and does not stop the others or reach the caller. This is the one
 * deliberate broad catch in the core.
 */
public final class CompositeRunObserver implements RunObserver {

    private static final System.Logger LOG = System.getLogger(CompositeRunObserver.class.getName());

    private final List<RunObserver> observers;

    private CompositeRunObserver(List<RunObserver> observers) {
        this.observers = observers;
    }

    public static CompositeRunObserver of(List<RunObserver> observers) {
        Objects.requireNonNull(observers, "observers is required");
        return new CompositeRunObserver(observers.stream().map(o -> Objects.requireNonNull(o, "observer")).toList());
    }

    @Override
    public void runChanged(PipelineRun run) {
        for (RunObserver observer : observers) {
            try {
                observer.runChanged(run);
            } catch (RuntimeException e) {
                warn(run.id().toString(), observer, e);
            }
        }
    }

    @Override
    public void stepChanged(PipelineStep step) {
        for (RunObserver observer : observers) {
            try {
                observer.stepChanged(step);
            } catch (RuntimeException e) {
                warn(step.runId().toString(), observer, e);
            }
        }
    }

    private static void warn(String runId, RunObserver observer, RuntimeException e) {
        LOG.log(System.Logger.Level.WARNING, "Run observer {0} failed for run {1}: {2}: {3}",
                observer.getClass().getName(), runId, e.getClass().getName(), e.getMessage());
    }
}
