package org.cttelsamicsterrassa.data.pipeline.runtime.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;

/**
 * Run and step meters. A terminal run status is written once and a step attempt finishes once, so each is recorded
 * once. Tags are enum or failure-code names only: a run id, season, scope or message is never a tag.
 */
public final class RunMetricsObserver implements RunObserver {

    static final String RUNS_FINISHED = "pipeline.runs.finished";
    static final String RUN_DURATION = "pipeline.run.duration";
    static final String STEP_DURATION = "pipeline.step.duration";

    private static final Duration[] BUCKETS = {
        Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(5),
        Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2),
        Duration.ofHours(3), Duration.ofHours(6)
    };

    private final MeterRegistry registry;
    private final PipelineRunRepository runs;

    public RunMetricsObserver(MeterRegistry registry, PipelineRunRepository runs) {
        this.registry = Objects.requireNonNull(registry, "registry is required");
        this.runs = Objects.requireNonNull(runs, "runs is required");
    }

    @Override
    public void runChanged(PipelineRun run) {
        if (!run.status().isTerminal()) {
            return;
        }
        String source = run.source().name();
        String trigger = run.trigger().name();
        String outcome = run.status().name();
        Counter.builder(RUNS_FINISHED)
                .tag("source", source).tag("trigger", trigger).tag("outcome", outcome)
                .tag("error", run.error() == null ? "none" : run.error().code())
                .register(registry).increment();
        if (run.startedAt() != null && run.finishedAt() != null) {
            timer(RUN_DURATION, "source", source, "trigger", trigger, "outcome", outcome)
                    .record(Duration.between(run.startedAt(), run.finishedAt()));
        }
    }

    @Override
    public void stepChanged(PipelineStep step) {
        if (step.status() == StepStatus.RUNNING) {
            return;
        }
        PipelineSource source = runs.findById(step.runId())
                .orElseThrow(() -> new IllegalStateException("run " + step.runId() + " of a finished step is missing"))
                .source();
        timer(STEP_DURATION, "source", source.name(), "step", step.kind().name(), "status", step.status().name())
                .record(Duration.between(step.startedAt(), step.finishedAt()));
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).serviceLevelObjectives(BUCKETS).register(registry);
    }
}
