package org.cttelsamicsterrassa.data.core.domain.load.model;

import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The incremental-upload classification of one import preview (FEAT-00088): acta buckets, the
 * planned changes per competition/group/phase, the current and projected jornada progress and the
 * duplicated {@code id_partido}s of the snapshot. Purely informational: it never changes the
 * preview status, never skips files and never feeds the real import run.
 *
 * <p>Lists are copied and sorted deterministically (competition, group, phase; nulls last - the
 * {@link org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgressCalculator} order;
 * duplicate fixture ids by id) so previews are comparable.</p>
 */
public record ImportPreviewClassification(
        PreviewActaCounts actas,
        List<PreviewScopeChanges> changes,
        long teamsPendingRegistration,
        List<RoundProgress> currentProgress,
        List<RoundProgress> projectedProgress,
        List<PreviewDuplicateFixtureId> duplicateFixtureIds) {

    public ImportPreviewClassification {
        actas = actas == null ? PreviewActaCounts.empty() : actas;
        changes = sortedChanges(changes);
        currentProgress = currentProgress == null ? List.of() : List.copyOf(currentProgress);
        projectedProgress = projectedProgress == null ? List.of() : List.copyOf(projectedProgress);
        duplicateFixtureIds = sortedDuplicates(duplicateFixtureIds);
        if (teamsPendingRegistration < 0) {
            throw new IllegalArgumentException(
                    "teamsPendingRegistration must not be negative, was " + teamsPendingRegistration);
        }
    }

    public static ImportPreviewClassification empty() {
        return new ImportPreviewClassification(PreviewActaCounts.empty(), List.of(), 0,
                List.of(), List.of(), List.of());
    }

    private static List<PreviewScopeChanges> sortedChanges(List<PreviewScopeChanges> changes) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        List<PreviewScopeChanges> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator
                .comparing(PreviewScopeChanges::competition, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PreviewScopeChanges::groupNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PreviewScopeChanges::phase, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(sorted);
    }

    private static List<PreviewDuplicateFixtureId> sortedDuplicates(List<PreviewDuplicateFixtureId> duplicates) {
        if (duplicates == null || duplicates.isEmpty()) {
            return List.of();
        }
        List<PreviewDuplicateFixtureId> sorted = new ArrayList<>(duplicates);
        sorted.sort(Comparator.comparing(PreviewDuplicateFixtureId::sourceFixtureId));
        return List.copyOf(sorted);
    }
}
