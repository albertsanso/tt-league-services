package org.cttelsamicsterrassa.data.load.shared.traverse;

/**
 * What one traversal did.
 *
 * @param filesSeen         report files encountered under the base folder
 * @param dispatched        files whose context reached at least one processor
 * @param skipped           files skipped because the name could not be parsed or the payload could
 *                          not be read
 * @param processorFailures individual processor invocations that threw
 */
public record TraversalSummary(long filesSeen, long dispatched, long skipped, long processorFailures) {

    @Override
    public String toString() {
        return "%d files seen, %d dispatched, %d skipped, %d processor failures"
                .formatted(filesSeen, dispatched, skipped, processorFailures);
    }
}
