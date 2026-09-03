package org.cttelsamicsterrassa.data.core.application.importresource.find.dto;

import java.util.List;

public record PendingImportsInfoDto(
        List<SourcePendingImportInfo> sources
) {
}
