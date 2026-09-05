package org.cttelsamicsterrassa.data.load.shared.traverse;

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
 */
public record TraversalSummary(long filesSeen, long dispatched, long skipped, long processorFailures,
                               List<ImportExecutionIssue> issues) {
    public TraversalSummary(long filesSeen, long dispatched, long skipped, long processorFailures) {
        this(filesSeen, dispatched, skipped, processorFailures, List.of());
    }

    public TraversalSummary {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TraversalSummary that
                && filesSeen == that.filesSeen && dispatched == that.dispatched
                && skipped == that.skipped && processorFailures == that.processorFailures;
    }

    @Override
    public int hashCode() {
        return Objects.hash(filesSeen, dispatched, skipped, processorFailures);
    }

    @Override
    public String toString() {
        return "%d files seen, %d dispatched, %d skipped, %d processor failures"
                .formatted(filesSeen, dispatched, skipped, processorFailures);
    }
}
