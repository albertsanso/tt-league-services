package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;

public record ImportExecutionMetrics(long filesSeen, long itemsDispatched, long skipped,
                                     long processorFailures, long persistenceWrites, long elapsedMillis,
                                     ImportLifecycleCounters lifecycle) {
    public ImportExecutionMetrics(long filesSeen, long itemsDispatched, long skipped,
                                  long processorFailures, long persistenceWrites, long elapsedMillis) {
        this(filesSeen, itemsDispatched, skipped, processorFailures, persistenceWrites, elapsedMillis,
                ImportLifecycleCounters.ZERO);
    }

    public ImportExecutionMetrics {
        lifecycle = lifecycle == null ? ImportLifecycleCounters.ZERO : lifecycle;
    }
}
