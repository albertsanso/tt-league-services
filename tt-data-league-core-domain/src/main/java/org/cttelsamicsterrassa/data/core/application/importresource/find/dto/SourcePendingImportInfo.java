package org.cttelsamicsterrassa.data.core.application.importresource.find.dto;

import java.time.ZonedDateTime;

public record SourcePendingImportInfo(
        String sourceName,
        ZonedDateTime uploadDateTime) {
}
