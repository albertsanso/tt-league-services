package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher.LaunchRequest;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ActiveRunConflictException;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;

/**
 * The only creator of UNIT_RETRY runs. A unit retry re-runs the full chain (ingest, fetch, import) for the scope of one
 * failed or skipped unit as a new run; the original run and unit stay unchanged, so history is never rewritten. Like a
 * replay it is never queued behind an active run.
 */
public final class RetryUnit {

    public sealed interface Outcome permits Created, NotFound, NotRetryable, Rejected {
    }

    public record Created(PipelineRun run) implements Outcome {
    }

    /** The run or the unit does not exist, or the unit belongs to another run. */
    public record NotFound() implements Outcome {
    }

    public record NotRetryable(String code, String message) implements Outcome {
    }

    public record Rejected(String code, String message, UUID activeRunId) implements Outcome {
    }

    public static final String ACTIVE_RUN = "ACTIVE_RUN";

    private final PipelineRunRepository runs;
    private final RunUnitRepository units;
    private final RunLauncher launcher;

    public RetryUnit(PipelineRunRepository runs, RunUnitRepository units, RunLauncher launcher) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.units = Objects.requireNonNull(units, "units is required");
        this.launcher = Objects.requireNonNull(launcher, "launcher is required");
    }

    public Outcome retry(UUID runId, UUID unitId, String requestedBy) {
        Optional<PipelineRun> foundRun = runs.findById(runId);
        Optional<RunUnit> foundUnit = units.findById(unitId);
        if (foundRun.isEmpty() || foundUnit.isEmpty() || !foundUnit.get().runId().equals(runId)) {
            return new NotFound();
        }
        PipelineRun original = foundRun.get();
        RunUnit unit = foundUnit.get();
        UnitRetryEligibility eligibility =
                UnitRetryRules.check(original, unit, runs.findActiveBySource(original.source()));
        if (!eligibility.allowed()) {
            return new NotRetryable(eligibility.code(), message(eligibility.code(), original, unit));
        }
        try {
            return new Created(launcher.launch(new LaunchRequest(original.source(), original.season(), unit.scope(),
                    original.force(), RunTrigger.UNIT_RETRY, requestedBy, original.id(), unit.id())));
        } catch (ActiveRunConflictException conflict) {
            UUID activeId = runs.findActiveBySource(original.source()).map(PipelineRun::id).orElse(null);
            return new Rejected(ACTIVE_RUN, "Source " + original.source() + " already has an active run", activeId);
        }
    }

    private static String message(String code, PipelineRun run, RunUnit unit) {
        return switch (code) {
            case UnitRetryEligibility.RUN_ACTIVE ->
                    "Run " + run.id() + " or its source " + run.source() + " still has an active run";
            default -> "Unit " + unit.label() + " is " + unit.status() + " and cannot be retried";
        };
    }
}
