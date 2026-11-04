package org.cttelsamicsterrassa.data.load.shared.traverse;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionIssue;

import java.util.List;
import java.util.Objects;
/**
 * What one traversal did.
 *
 * @param filesSeen         report files encountered under the base folder
 * @param dispatched        files whose context reached at least one processor
 * @param skipped           files skipped because the name could not be parsed or the payload could
 *                          not be read
 * @param processorFailures individual processor invocations that threw
 * @param lifecycle         what the run did to stored matches, including unresolved pending
 *                          fixtures (FEAT-00082)
 */
public record TraversalSummary(long filesSeen, long dispatched, long skipped, long processorFailures,
                               List<ImportExecutionIssue> issues, ImportLifecycleCounters lifecycle) {
    public TraversalSummary(long filesSeen, long dispatched, long skipped, long processorFailures) {
        this(filesSeen, dispatched, skipped, processorFailures, List.of(), ImportLifecycleCounters.ZERO);
    }

    public TraversalSummary(long filesSeen, long dispatched, long skipped, long processorFailures,
                            List<ImportExecutionIssue> issues) {
        this(filesSeen, dispatched, skipped, processorFailures, issues, ImportLifecycleCounters.ZERO);
    }

    public TraversalSummary {
        issues = issues == null ? List.of() : List.copyOf(issues);
        lifecycle = lifecycle == null ? ImportLifecycleCounters.ZERO : lifecycle;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TraversalSummary that
                && filesSeen == that.filesSeen && dispatched == that.dispatched
                && skipped == that.skipped && processorFailures == that.processorFailures
                && lifecycle.equals(that.lifecycle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filesSeen, dispatched, skipped, processorFailures, lifecycle);
    }

    @Override
    public String toString() {
        return "%d files seen, %d dispatched, %d skipped, %d processor failures, lifecycle %s"
                .formatted(filesSeen, dispatched, skipped, processorFailures, lifecycle);
    }
}
