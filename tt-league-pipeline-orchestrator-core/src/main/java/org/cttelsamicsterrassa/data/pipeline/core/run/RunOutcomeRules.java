package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The only place that derives the status of a run from its units. Pure: no I/O and no clock. The error is set exactly
 * when the derived status is FAILED.
 */
public final class RunOutcomeRules {

    /** The derived terminal status and, for FAILED, the run error. */
    public record Outcome(RunStatus status, RunError error) {

        public Outcome {
            Objects.requireNonNull(status, "status is required");
            if (!status.isTerminal()) {
                throw new IllegalArgumentException("A derived outcome must be terminal: " + status);
            }
            if ((status == RunStatus.FAILED) != (error != null)) {
                throw new IllegalArgumentException("error must be present exactly for a FAILED outcome");
            }
        }
    }

    private RunOutcomeRules() {
    }

    /**
     * Derives the run status from finished units:
     * <ul>
     *   <li>every unit {@code NO_CHANGES}: {@code NO_CHANGES};</li>
     *   <li>every unit {@code SUCCEEDED} or {@code NO_CHANGES}, at least one {@code SUCCEEDED}: {@code SUCCEEDED};</li>
     *   <li>any {@code PARTIAL} unit, or failed/skipped units next to successful ones: {@code PARTIAL};</li>
     *   <li>every unit {@code FAILED} or {@code SKIPPED}: {@code FAILED}. A single unit passes its error through
     *       unchanged; several units report the first failed unit's code and the list of codes.</li>
     * </ul>
     */
    public static Outcome derive(List<RunUnit> units) {
        Objects.requireNonNull(units, "units is required");
        if (units.isEmpty()) {
            throw new IllegalArgumentException("A run outcome needs at least one unit");
        }
        for (RunUnit unit : units) {
            if (unit.status().isActive()) {
                throw new IllegalArgumentException(
                        "Unit " + unit.id() + " is still " + unit.status() + "; cannot derive the run outcome");
            }
        }
        long noChanges = count(units, UnitStatus.NO_CHANGES);
        long succeeded = count(units, UnitStatus.SUCCEEDED);
        long partial = count(units, UnitStatus.PARTIAL);
        long unsuccessful = count(units, UnitStatus.FAILED) + count(units, UnitStatus.SKIPPED);
        if (noChanges == units.size()) {
            return new Outcome(RunStatus.NO_CHANGES, null);
        }
        if (unsuccessful == 0 && partial == 0) {
            return new Outcome(RunStatus.SUCCEEDED, null);
        }
        if (unsuccessful < units.size()) {
            return new Outcome(RunStatus.PARTIAL, null);
        }
        return new Outcome(RunStatus.FAILED, failure(units));
    }

    private static RunError failure(List<RunUnit> units) {
        if (units.size() == 1) {
            return units.get(0).error();
        }
        RunUnit first = units.stream()
                .filter(unit -> unit.status() == UnitStatus.FAILED)
                .findFirst()
                .orElse(units.get(0));
        Set<String> codes = new LinkedHashSet<>();
        units.forEach(unit -> codes.add(unit.error().code()));
        return new RunError(first.error().code(),
                units.size() + " of " + units.size() + " units failed: " + String.join(", ", codes));
    }

    private static long count(List<RunUnit> units, UnitStatus status) {
        return units.stream().filter(unit -> unit.status() == status).count();
    }
}
