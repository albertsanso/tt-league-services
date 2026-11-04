package org.cttelsamicsterrassa.data.pipeline.core.execution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunOutcomeRules;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitPlanner;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.StaleRunException;

/**
 * Drives one run: plans its units, executes them one after the other (ingest accepts one active run per source) and
 * derives the run status from the finished units. {@link #execute(UUID)} is the only entry point and serves fresh and
 * resumed runs: it continues from whatever run, unit and step rows are stored.
 */
public final class RunExecutor {

    private static final System.Logger LOG = System.getLogger(RunExecutor.class.getName());
    private static final int MAX_INTERNAL_MESSAGE = 500;

    private final PipelineRunRepository runs;
    private final RunUnitRepository units;
    private final PipelineStepRepository steps;
    private final RunArtifactRepository artifactRows;
    private final RunClock clock;
    private final RunObserver observer;
    private final UnitExecutor unitExecutor;

    public RunExecutor(
            PipelineRunRepository runs,
            RunUnitRepository units,
            PipelineStepRepository steps,
            RunArtifactRepository artifactRows,
            ImportReportRepository reports,
            IngestGateway ingest,
            ImportGateway importGateway,
            ArtifactStore artifacts,
            RunClock clock,
            RunObserver observer,
            ExecutionSettings settings) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.units = Objects.requireNonNull(units, "units is required");
        this.steps = Objects.requireNonNull(steps, "steps is required");
        this.artifactRows = Objects.requireNonNull(artifactRows, "artifactRows is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.observer = Objects.requireNonNull(observer, "observer is required");
        this.unitExecutor = new UnitExecutor(units, steps, artifactRows, reports, ingest, importGateway, artifacts,
                clock, observer, settings);
    }

    public void execute(UUID runId) {
        Optional<PipelineRun> loaded = runs.findById(runId);
        if (loaded.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "Run {0} does not exist; nothing to execute", runId);
            return;
        }
        if (loaded.get().status().isTerminal()) {
            return;
        }
        try {
            drive(loaded.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.INFO, "Run {0} interrupted; recovery resumes it", runId);
        } catch (StaleRunException e) {
            LOG.log(System.Logger.Level.WARNING, "Run {0} is owned by another executor; stopping", runId);
        } catch (RuntimeException e) {
            failUnexpected(runId, e);
        }
    }

    private void drive(PipelineRun start) throws InterruptedException {
        PipelineRun run = start;
        if (run.status() == RunStatus.QUEUED) {
            if (units.findByRunId(run.id()).isEmpty()) {
                List<RunUnit> planned = planUnits(run);
                if (planned.isEmpty()) {
                    failRun(run, new RunError(FailureCode.ARTIFACT_PURGED.name(),
                            "No stored package of run " + run.retryOfRunId() + " is available to replay"));
                    return;
                }
                units.addAll(planned);
            }
            run = updateRun(run.start(clock.now()));
        }
        List<RunUnit> planned = units.findByRunId(run.id());
        if (planned.isEmpty()) {
            throw new IllegalStateException("Run " + run.id() + " is " + run.status() + " but has no units");
        }
        for (RunUnit unit : planned) {
            if (unit.status().isTerminal()) {
                continue;
            }
            RunUnit finished = unitExecutor.execute(run, unit);
            if (finished.status() == UnitStatus.FAILED
                    && UnitExecutionRules.abortsRemaining(finished.error().code())) {
                skipPending(run, UnitExecutionRules.skipError(finished));
                break;
            }
        }
        RunOutcomeRules.Outcome outcome = RunOutcomeRules.derive(units.findByRunId(run.id()));
        updateRun(run.finish(outcome.status(), outcome.error(), clock.now()));
    }

    /**
     * A fresh run is split by {@link UnitPlanner}; a replay copies the units of the original that still have a stored
     * package (empty when none has), and a unit retry plans its single scope like any run.
     */
    private List<RunUnit> planUnits(PipelineRun run) {
        if (run.trigger() != RunTrigger.RETRY) {
            return UnitPlanner.plan(run.id(), run.scope());
        }
        Set<UUID> packaged = artifactRows.findByRunId(run.retryOfRunId()).stream()
                .filter(artifact -> artifact.kind() == ArtifactKind.ZIP && !artifact.isPurged())
                .map(RunArtifact::unitId)
                .collect(Collectors.toSet());
        List<RunUnit> originals = units.findByRunId(run.retryOfRunId()).stream()
                .filter(original -> packaged.contains(original.id()))
                .toList();
        return UnitPlanner.replan(run.id(), originals);
    }

    private void skipPending(PipelineRun run, RunError error) {
        for (RunUnit unit : units.findByRunId(run.id())) {
            if (unit.status() == UnitStatus.PENDING) {
                observer.unitChanged(units.update(unit.skip(error, clock.now())));
            }
        }
    }

    private PipelineRun failRun(PipelineRun run, RunError error) {
        return updateRun(run.fail(error, clock.now()));
    }

    private PipelineRun updateRun(PipelineRun run) {
        PipelineRun saved = runs.update(run);
        observer.runChanged(saved);
        return saved;
    }

    private void failUnexpected(UUID runId, RuntimeException cause) {
        PipelineRun run = runs.findById(runId).orElseThrow(() -> cause);
        LOG.log(System.Logger.Level.ERROR, "Run " + runId + " failed unexpectedly", cause);
        if (run.status().isTerminal()) {
            return;
        }
        String message = truncate(cause.getClass().getName() + ": " + cause.getMessage(), MAX_INTERNAL_MESSAGE);
        RunError error = new RunError(FailureCode.INTERNAL_ERROR.name(), message);
        for (PipelineStep step : steps.findByRunId(runId)) {
            if (step.status() == StepStatus.RUNNING) {
                observer.stepChanged(steps.save(step.fail(clock.now(), null, error, false)));
            }
        }
        RunError skipped = new RunError(FailureCode.UNIT_SKIPPED.name(),
                "skipped after " + FailureCode.INTERNAL_ERROR.name());
        // Units run one after the other, so the first unfinished unit is the one being executed.
        Optional<RunUnit> active = units.findByRunId(runId).stream()
                .filter(unit -> unit.status().isActive())
                .findFirst();
        if (active.isPresent()) {
            RunUnit failed = units.update(active.get().fail(error, clock.now()));
            observer.unitChanged(failed);
            skipped = UnitExecutionRules.skipError(failed);
        }
        skipPending(run, skipped);
        failRun(run, error);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
