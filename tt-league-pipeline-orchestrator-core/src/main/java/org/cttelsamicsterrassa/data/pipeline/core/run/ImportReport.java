package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Platform import counters summed over the job's seasons, plus the raw job JSON as opaque text. */
public record ImportReport(
        UUID runId,
        UUID importJobId,
        String importStatus,
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
        long amendedPlayed,
        List<String> issues,
        String rawReport,
        Instant receivedAt) {

    private static final Set<String> STATUSES = Set.of("SUCCEEDED", "PARTIAL", "FAILED");

    public ImportReport {
        Checks.required(runId, "runId");
        Checks.required(importJobId, "importJobId");
        Checks.required(importStatus, "importStatus");
        if (!STATUSES.contains(importStatus)) {
            throw new IllegalArgumentException("importStatus must be one of " + STATUSES + ": " + importStatus);
        }
        Checks.nonNegative(filesSeen, "filesSeen");
        Checks.nonNegative(itemsPersisted, "itemsPersisted");
        Checks.nonNegative(skipped, "skipped");
        Checks.nonNegative(processorFailures, "processorFailures");
        Checks.nonNegative(scheduledCreated, "scheduledCreated");
        Checks.nonNegative(upgradedToPlayed, "upgradedToPlayed");
        Checks.nonNegative(rescheduled, "rescheduled");
        Checks.nonNegative(partialActas, "partialActas");
        Checks.nonNegative(invalidActas, "invalidActas");
        Checks.nonNegative(unresolvedPendingFixtures, "unresolvedPendingFixtures");
        Checks.nonNegative(amendedPlayed, "amendedPlayed");
        issues = List.copyOf(Checks.required(issues, "issues"));
        Checks.nonBlank(rawReport, "rawReport");
        Checks.required(receivedAt, "receivedAt");
    }
}
