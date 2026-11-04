package org.cttelsamicsterrassa.data.core.application.importresource.preview.dto;

import org.cttelsamicsterrassa.data.core.application.importresource.shared.dto.RoundProgressDto;

import java.util.List;

/**
 * The incremental-upload classification block of an import preview response (FEAT-00088): acta
 * buckets, planned changes per competition/group/phase, the current and projected jornada progress
 * and the duplicated {@code id_partido}s of the snapshot. Informational only.
 */
public record ImportPreviewClassificationDto(
        PreviewActaCountsDto actas,
        List<PreviewScopeChangesDto> changes,
        long teamsPendingRegistration,
        List<RoundProgressDto> currentProgress,
        List<RoundProgressDto> projectedProgress,
        List<PreviewDuplicateFixtureIdDto> duplicateFixtureIds) {
}
