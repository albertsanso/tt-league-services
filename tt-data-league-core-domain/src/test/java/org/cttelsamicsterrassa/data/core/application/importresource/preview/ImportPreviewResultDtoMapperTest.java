package org.cttelsamicsterrassa.data.core.application.importresource.preview;

import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewResultDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewClassification;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewActaCounts;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewDuplicateFixtureId;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewScopeChanges;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00088: the preview DTO mapper fills the classification block, and a missing resource maps to
 * an empty classification rather than a null one.
 */
class ImportPreviewResultDtoMapperTest {

    @Test
    void mapsAPopulatedClassification() {
        ImportResource importResource = importResource();
        ImportPreviewClassification classification = new ImportPreviewClassification(
                new PreviewActaCounts(3, 9, 1, 0, 2),
                List.of(new PreviewScopeChanges("tercera-nacional-masculino", 1, "1a Fase",
                        9, 3, 0, 0, 0, 0, 0, 0, 0, 0)),
                4,
                List.of(progress(1, 3)),
                List.of(progress(2, 5)),
                List.of(new PreviewDuplicateFixtureId("dup-id", List.of("a.json", "b.json"))));
        ImportPreviewResult result = ImportPreviewResult.success(
                List.of(), List.of(), 15, 15, 0, 0, classification);

        ImportPreviewResultDto dto = ImportPreviewResultDtoMapper.toDto(importResource, result);

        assertEquals(3, dto.classification().actas().published());
        assertEquals(9, dto.classification().actas().unpublished());
        assertEquals(1, dto.classification().actas().partial());
        assertEquals(2, dto.classification().actas().unresolved());
        assertEquals(1, dto.classification().changes().size());
        assertEquals("tercera-nacional-masculino", dto.classification().changes().get(0).competition());
        assertEquals(9, dto.classification().changes().get(0).newScheduled());
        assertEquals(3, dto.classification().changes().get(0).newPlayed());
        assertEquals(4, dto.classification().teamsPendingRegistration());
        assertEquals(1, dto.classification().currentProgress().size());
        assertEquals(1, dto.classification().currentProgress().get(0).currentRound());
        assertEquals(2, dto.classification().projectedProgress().get(0).currentRound());
        assertEquals(1, dto.classification().duplicateFixtureIds().size());
        assertEquals("dup-id", dto.classification().duplicateFixtureIds().get(0).sourceFixtureId());
        assertEquals(List.of("a.json", "b.json"), dto.classification().duplicateFixtureIds().get(0).locations());
    }

    @Test
    void missingResourceCarriesAnEmptyClassification() {
        ImportPreviewResultDto dto = ImportPreviewResultDtoMapper.missingResource(UUID.randomUUID());

        assertEquals("failure", dto.status());
        assertEquals(0, dto.classification().actas().published());
        assertTrue(dto.classification().changes().isEmpty());
        assertTrue(dto.classification().currentProgress().isEmpty());
        assertTrue(dto.classification().projectedProgress().isEmpty());
        assertTrue(dto.classification().duplicateFixtureIds().isEmpty());
    }

    private static RoundProgress progress(int currentRound, long played) {
        return new RoundProgress(ImportSource.FCTT, Season.of(2026), "tercera-nacional-masculino", 1,
                "1a Fase", currentRound, null, 3, played);
    }

    private static ImportResource importResource() {
        Resource resource = Resource.createExisting(
                UUID.randomUUID(), "ACTAS", "import/FCTT/ACTAS", Path.of("import-fctt", "actas"));
        return ImportResource.createExisting(
                UUID.randomUUID(), resource, Optional.empty(), ResourceType.ACTAS, ZonedDateTime.now(),
                Optional.empty(), Season.of(2026), ImportSource.FCTT, ImportResourceStatus.PENDING);
    }
}
