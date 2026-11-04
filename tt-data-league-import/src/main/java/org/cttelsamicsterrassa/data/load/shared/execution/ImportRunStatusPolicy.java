package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;

/**
 * The single run-status rule (FEAT-00082): failures and issues fail the run, an empty result means
 * no acta was found at all, and everything else — including runs where every acta was already
 * stored unchanged and runs whose only actas are unresolved pending placeholders — succeeds.
 *
 * <p>Reported match outcomes (PARTIAL, INVALID, regression, unresolved placeholders) are warnings
 * and never reach {@code hasIssues}: they must not fail the run or skip consolidation.</p>
 */
public final class ImportRunStatusPolicy {

    private ImportRunStatusPolicy() {
    }

    public static ImportProcessStatus statusOf(long processorFailures, boolean hasIssues, long dispatched,
                                               ImportLifecycleCounters lifecycle) {
        if (processorFailures > 0 || hasIssues) {
            return ImportProcessStatus.FAILURE;
        }
        ImportLifecycleCounters effective = lifecycle == null ? ImportLifecycleCounters.ZERO : lifecycle;
        if (dispatched == 0 && effective.unresolvedPendingFixtures() == 0) {
            return ImportProcessStatus.EMPTY_RESULT;
        }
        return ImportProcessStatus.SUCCESS;
    }
}
