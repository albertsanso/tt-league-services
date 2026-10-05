package org.cttelsamicsterrassa.data.core.domain.load.model;

import java.util.Objects;

/**
 * What one incremental import run did to stored matches (FEAT-00082). The components mirror the
 * per-acta mutually exclusive {@code MatchLifecycleOutcome}s of FEAT-00081, so an acta is counted
 * once: a PARTIAL or INVALID acta that created or rescheduled a SCHEDULED match counts only under
 * {@code partialActas} / {@code invalidActas}.
 *
 * @param scheduledCreated           pending fixtures stored as new SCHEDULED matches
 * @param upgradedToPlayed           stored SCHEDULED matches upgraded in place to PLAYED
 * @param rescheduled                stored SCHEDULED matches whose schedule changed
 * @param partialActas               partial actas kept scheduled and reported
 * @param invalidActas               invalid actas kept scheduled (or untouched) and reported
 * @param unresolvedPendingFixtures  pending fixtures without teams that could not be dispatched
 * @param amendedPlayed              stored PLAYED matches re-applied from an amended acta (FEAT-00089)
 */
public record ImportLifecycleCounters(long scheduledCreated, long upgradedToPlayed, long rescheduled,
                                      long partialActas, long invalidActas, long unresolvedPendingFixtures,
                                      long amendedPlayed) {

    public static final ImportLifecycleCounters ZERO = new ImportLifecycleCounters(0, 0, 0, 0, 0, 0, 0);

    public ImportLifecycleCounters {
        requireNonNegative(scheduledCreated, "scheduledCreated");
        requireNonNegative(upgradedToPlayed, "upgradedToPlayed");
        requireNonNegative(rescheduled, "rescheduled");
        requireNonNegative(partialActas, "partialActas");
        requireNonNegative(invalidActas, "invalidActas");
        requireNonNegative(unresolvedPendingFixtures, "unresolvedPendingFixtures");
        requireNonNegative(amendedPlayed, "amendedPlayed");
    }

    public ImportLifecycleCounters plus(ImportLifecycleCounters other) {
        Objects.requireNonNull(other, "other");
        return new ImportLifecycleCounters(scheduledCreated + other.scheduledCreated,
                upgradedToPlayed + other.upgradedToPlayed,
                rescheduled + other.rescheduled,
                partialActas + other.partialActas,
                invalidActas + other.invalidActas,
                unresolvedPendingFixtures + other.unresolvedPendingFixtures,
                amendedPlayed + other.amendedPlayed);
    }

    /** Whether any component is greater than zero. */
    public boolean hasActivity() {
        return scheduledCreated > 0 || upgradedToPlayed > 0 || rescheduled > 0
                || partialActas > 0 || invalidActas > 0 || unresolvedPendingFixtures > 0
                || amendedPlayed > 0;
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
    }
}
