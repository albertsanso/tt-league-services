package org.cttelsamicsterrassa.data.core.application.importresource.find.dto;

import org.cttelsamicsterrassa.data.core.application.importresource.shared.dto.RoundProgressDto;

import java.util.List;
import java.util.UUID;

public record ImportResourceDto(
        UUID importResourceId,
        String source,
        String season,
        String resourceType,
        String status,
        String createdDate,
        String lastProcessedDate,
        List<RoundProgressDto> roundProgress
) {

    public ImportResourceDto {
        roundProgress = roundProgress == null ? List.of() : List.copyOf(roundProgress);
    }

    /**
     * Read-model rows existed before jornada progress (FEAT-00084) and stay valid without it.
     */
    public ImportResourceDto(UUID importResourceId, String source, String season, String resourceType,
                             String status, String createdDate, String lastProcessedDate) {
        this(importResourceId, source, season, resourceType, status, createdDate, lastProcessedDate, List.of());
    }
}
