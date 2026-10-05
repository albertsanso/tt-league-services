package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

public record ImportCounters(
        long filesSeen,
        long itemsPersisted,
        long skipped,
        long processorFailures,
        long scheduledCreated,
        long upgradedToPlayed,
        long rescheduled,
        long partialActas,
        long invalidActas,
        long unresolvedPendingFixtures,
        long amendedPlayed) {

    public ImportCounters {
        long[] all = {filesSeen, itemsPersisted, skipped, processorFailures, scheduledCreated, upgradedToPlayed,
            rescheduled, partialActas, invalidActas, unresolvedPendingFixtures, amendedPlayed};
        for (long value : all) {
            if (value < 0) {
                throw new IllegalArgumentException("import counters must not be negative");
            }
        }
    }
}
