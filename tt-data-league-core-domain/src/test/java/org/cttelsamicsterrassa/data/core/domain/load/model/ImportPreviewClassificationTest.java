package org.cttelsamicsterrassa.data.core.domain.load.model;

import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00088: invariant and deterministic-ordering tests for the preview classification records.
 */
class ImportPreviewClassificationTest {

    private static final Season SEASON = Season.of(2026);

    @Test
    void actaCountsRejectNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> new PreviewActaCounts(-1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PreviewActaCounts(0, 0, 0, 0, -1));
        assertEquals(0, PreviewActaCounts.empty().published());
    }

    @Test
    void scopeChangesRejectNegativeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new PreviewScopeChanges("c", 1, null, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void duplicateFixtureIdRequiresIdAndAtLeastTwoLocations() {
        assertThrows(IllegalArgumentException.class, () -> new PreviewDuplicateFixtureId(" ", List.of("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> new PreviewDuplicateFixtureId("id", List.of("a")));
        PreviewDuplicateFixtureId duplicate = new PreviewDuplicateFixtureId("id", List.of("a", "b"));
        assertEquals(List.of("a", "b"), duplicate.locations());
    }

    @Test
    void classificationSortsChangesAndDuplicatesAndCopiesLists() {
        PreviewScopeChanges nullGroup = new PreviewScopeChanges("bbb", null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        PreviewScopeChanges groupOne = new PreviewScopeChanges("aaa", 1, "1a Fase", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        PreviewScopeChanges groupTwo = new PreviewScopeChanges("aaa", 2, null, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0);

        ImportPreviewClassification classification = new ImportPreviewClassification(
                new PreviewActaCounts(1, 1, 0, 0, 0),
                new java.util.ArrayList<>(List.of(nullGroup, groupTwo, groupOne)),
                2,
                List.of(progress("aaa", 1, "1a Fase")),
                List.of(progress("aaa", 1, "1a Fase")),
                new java.util.ArrayList<>(List.of(
                        new PreviewDuplicateFixtureId("z-id", List.of("f1", "f2")),
                        new PreviewDuplicateFixtureId("a-id", List.of("f3", "f4")))));

        assertEquals(List.of("aaa", "aaa", "bbb"),
                classification.changes().stream().map(PreviewScopeChanges::competition).toList());
        assertEquals(1, classification.changes().get(0).groupNumber());
        assertEquals(2, classification.changes().get(1).groupNumber());
        assertEquals("a-id", classification.duplicateFixtureIds().get(0).sourceFixtureId());
        assertEquals(2, classification.teamsPendingRegistration());
        assertEquals(1, classification.currentProgress().size());
    }

    @Test
    void emptyClassificationHasEmptyBucketsAndLists() {
        ImportPreviewClassification empty = ImportPreviewClassification.empty();
        assertEquals(0, empty.actas().published());
        assertTrue(empty.changes().isEmpty());
        assertTrue(empty.currentProgress().isEmpty());
        assertTrue(empty.projectedProgress().isEmpty());
        assertTrue(empty.duplicateFixtureIds().isEmpty());
        assertEquals(0, empty.teamsPendingRegistration());
    }

    @Test
    void resultFactoriesDefaultToEmptyClassification() {
        ImportPreviewResult success = ImportPreviewResult.success(List.of(), List.of(), 1, 1, 0, 0);
        assertEquals(0, success.classification().actas().published());

        ImportPreviewClassification classification = new ImportPreviewClassification(
                new PreviewActaCounts(3, 9, 0, 0, 0), List.of(), 0, List.of(), List.of(), List.of());
        ImportPreviewResult withClassification = ImportPreviewResult.success(
                List.of(), List.of(), 12, 12, 0, 0, classification);
        assertEquals(3, withClassification.classification().actas().published());
        assertEquals(9, withClassification.classification().actas().unpublished());
    }

    private static RoundProgress progress(String competition, Integer groupNumber, String phase) {
        return new RoundProgress(ImportSource.FCTT, SEASON, competition, groupNumber, phase, 1, null, 3, 1);
    }
}
