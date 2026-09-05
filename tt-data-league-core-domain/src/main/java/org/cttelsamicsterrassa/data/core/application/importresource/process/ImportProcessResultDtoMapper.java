package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportProcessResultDto;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewFindingDto;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewProcessingErrorDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;

import java.util.List;
import java.util.UUID;

final class ImportProcessResultDtoMapper {
    private ImportProcessResultDtoMapper() {
    }

    static ImportProcessResultDto toDto(ImportResource resource, ImportProcessResult result) {
        return dto(resource.getId(), resource.getSource().name(), resource.getSeason().toString(),
                resource.getType().name(), result);
    }

    static ImportProcessResultDto missingResource(UUID id) {
        return dto(id, null, null, null, ImportProcessResult.failure(List.of(),
                List.of(new ImportPreviewProcessingError("Import resource not found: " + id, null)),
                0, 0, 0, 0));
    }

    static ImportProcessResultDto alreadyProcessing(UUID id) {
        return dto(id, null, null, null, ImportProcessResult.failure(List.of(),
                List.of(new ImportPreviewProcessingError("Import resource is already processing: " + id, null)),
                0, 0, 0, 0));
    }

    private static ImportProcessResultDto dto(UUID id, String source, String season, String type,
                                              ImportProcessResult result) {
        return new ImportProcessResultDto(id, source, season, type, result.status().value(),
                result.findings().stream().map(f -> new ImportPreviewFindingDto(
                        f.severity(), f.message(), f.location())).toList(),
                result.processingErrors().stream().map(e -> new ImportPreviewProcessingErrorDto(
                        e.message(), e.location())).toList(),
                result.filesSeen(), result.itemsPersisted(), result.skipped(), result.processorFailures(),
                result.elapsedMillis(), result.persistenceWrites(), result.executionIssues(),
                result.postProcessingOutcomes());
    }
}
