package org.cttelsamicsterrassa.data.load.shared.classify;

import java.util.Objects;

/**
 * The result of classifying one acta/fixture: its {@link ActaCompleteness}, a human-readable reason
 * (used later for issue reports), and whether it is an unresolved pending fixture (missing team
 * names).
 */
public record ActaClassification(
        ActaCompleteness completeness,
        String reason,
        boolean unresolvedPendingFixture) {

    public ActaClassification {
        Objects.requireNonNull(completeness, "completeness");
        Objects.requireNonNull(reason, "reason");
        if (unresolvedPendingFixture && completeness != ActaCompleteness.PENDING) {
            throw new IllegalArgumentException("unresolvedPendingFixture requires completeness == PENDING");
        }
    }

    public boolean isPlayed() {
        return completeness == ActaCompleteness.PLAYED;
    }
}
