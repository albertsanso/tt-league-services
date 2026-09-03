package org.cttelsamicsterrassa.data.core.application.importresource.find.dto;

import java.util.UUID;

public record ImportResourceDto(
        UUID importResourceId,
        String source,
        String season,
        String resourceType,
        String status,
        String createdDate,
        String lastProcessedDate
) {
}
