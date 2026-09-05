package org.cttelsamicsterrassa.data.load.shared.execution;

public record ImportExecutionMetrics(long filesSeen, long itemsDispatched, long skipped,
                                     long processorFailures, long persistenceWrites, long elapsedMillis) {
}
