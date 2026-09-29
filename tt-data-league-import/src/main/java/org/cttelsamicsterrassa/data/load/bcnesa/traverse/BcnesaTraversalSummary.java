package org.cttelsamicsterrassa.data.load.bcnesa.traverse;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionIssue;

import java.util.List;
import java.util.Objects;
/**
 * What one BCNESA traversal did.
 *
 * <p>A BCNESA file is split into fixtures (one per file throughout the current export, but a file
 * may hold a whole matchday), so counts are kept at both levels: {@code filesSeen}/{@code filesSkipped}
 * describe the walk over the {@code acta*.json} files, {@code fixturesSeen}/{@code fixturesDispatched}/
 * {@code fixturesUnresolved} describe what came out of splitting them.</p>
 *
 * <p>Measured over the export: the 16,387 legacy files (2020-2021 to 2025-2026), named
 * {@code acta_<jornada>_page_<n>.json} or {@code acta_<n>.json}, each hold exactly one fixture; the
 * 2,882 unpublished files of 2026-2027, named {@code acta_<homeId>-<awayId>_<jornada>.json}, hold no
 * games, so a file yields one fixture there too.</p>
 *
 * @param filesSeen          match report files encountered under the base folder
 * @param filesSkipped       files skipped because the payload could not be parsed or carried no
 *                           match day
 * @param fixturesSeen       fixtures produced by splitting the files that were read
 * @param fixturesDispatched fixtures whose context reached at least one processor
 * @param fixturesUnresolved fixtures skipped because their clubs could not be attributed
 * @param processorFailures  individual processor invocations that threw
 * @param lifecycle          what the run did to stored matches, including unresolved pending
 *                           fixtures (FEAT-00082)
 */
public record BcnesaTraversalSummary(long filesSeen,
                                     long filesSkipped,
                                     long fixturesSeen,
                                     long fixturesDispatched,
                                     long fixturesUnresolved,
                                     long processorFailures,
                                     List<ImportExecutionIssue> issues,
                                     ImportLifecycleCounters lifecycle) {
    public BcnesaTraversalSummary(long filesSeen, long filesSkipped, long fixturesSeen,
                                   long fixturesDispatched, long fixturesUnresolved, long processorFailures) {
        this(filesSeen, filesSkipped, fixturesSeen, fixturesDispatched, fixturesUnresolved,
                processorFailures, List.of(), ImportLifecycleCounters.ZERO);
    }

    public BcnesaTraversalSummary(long filesSeen, long filesSkipped, long fixturesSeen,
                                   long fixturesDispatched, long fixturesUnresolved, long processorFailures,
                                   List<ImportExecutionIssue> issues) {
        this(filesSeen, filesSkipped, fixturesSeen, fixturesDispatched, fixturesUnresolved,
                processorFailures, issues, ImportLifecycleCounters.ZERO);
    }

    public BcnesaTraversalSummary {
        issues = issues == null ? List.of() : List.copyOf(issues);
        lifecycle = lifecycle == null ? ImportLifecycleCounters.ZERO : lifecycle;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BcnesaTraversalSummary that
                && filesSeen == that.filesSeen && filesSkipped == that.filesSkipped
                && fixturesSeen == that.fixturesSeen && fixturesDispatched == that.fixturesDispatched
                && fixturesUnresolved == that.fixturesUnresolved
                && processorFailures == that.processorFailures
                && lifecycle.equals(that.lifecycle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filesSeen, filesSkipped, fixturesSeen, fixturesDispatched,
                fixturesUnresolved, processorFailures, lifecycle);
    }

    @Override
    public String toString() {
        return "%d files seen, %d files skipped, %d fixtures seen, %d dispatched, %d unresolved, "
                + "%d processor failures, lifecycle %s"
                .formatted(filesSeen, filesSkipped, fixturesSeen, fixturesDispatched, fixturesUnresolved,
                        processorFailures, lifecycle);
    }
}
