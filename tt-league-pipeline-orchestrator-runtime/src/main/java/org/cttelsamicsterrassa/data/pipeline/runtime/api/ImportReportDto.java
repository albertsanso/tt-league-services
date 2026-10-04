package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;

/** Platform import counters of a run; the raw job JSON is not exposed. */
public record ImportReportDto(
        String status,
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
        Instant receivedAt) {
}
