package org.cttelsamicsterrassa.data.core.domain.load.model;

/**
 * What an import preview would change within one competition, group and phase (FEAT-00088): one
 * count per kind of planned lifecycle change. The counts are projections of the same decision the
 * import run applies; they never decide whether anything is imported. Informational only.
 */
public record PreviewScopeChanges(
        String competition,
        Integer groupNumber,
        String phase,
        long newScheduled,
        long newPlayed,
        long upgrades,
        long reschedules,
        long unchanged,
        long playedKept,
        long regressions,
        long invalidOnPlayed,
        long identityConflicts,
        long notStored) {

    public PreviewScopeChanges {
        requireNonNegative(newScheduled, "newScheduled");
        requireNonNegative(newPlayed, "newPlayed");
        requireNonNegative(upgrades, "upgrades");
        requireNonNegative(reschedules, "reschedules");
        requireNonNegative(unchanged, "unchanged");
        requireNonNegative(playedKept, "playedKept");
        requireNonNegative(regressions, "regressions");
        requireNonNegative(invalidOnPlayed, "invalidOnPlayed");
        requireNonNegative(identityConflicts, "identityConflicts");
        requireNonNegative(notStored, "notStored");
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
    }
}
