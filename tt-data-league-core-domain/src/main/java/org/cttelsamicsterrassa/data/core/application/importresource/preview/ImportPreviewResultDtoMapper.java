package org.cttelsamicsterrassa.data.core.application.importresource.preview;

import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewFindingDto;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewProcessingErrorDto;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewResultDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewFinding;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;

import java.util.List;
import java.util.UUID;

final class ImportPreviewResultDtoMapper {

    private ImportPreviewResultDtoMapper() {
    }

    static ImportPreviewResultDto toDto(ImportResource importResource, ImportPreviewResult result) {
        return new ImportPreviewResultDto(
                importResource.getId(),
                importResource.getSource().name(),
                importResource.getSeason().toString(),
                importResource.getType().name(),
                result.status().value(),
                findings(result.validationFindings()),
                errors(result.processingErrors()),
                result.filesSeen(),
                result.itemsDispatched(),
                result.skipped(),
                result.processorFailures());
    }

    static ImportPreviewResultDto missingResource(UUID importResourceId) {
        ImportPreviewResult result = ImportPreviewResult.failure(
                List.of(),
                List.of(new ImportPreviewProcessingError(
                        "Import resource not found: " + importResourceId,
                        null)),
                0,
                0,
                0,
                0);
        return new ImportPreviewResultDto(
                importResourceId,
                null,
                null,
                null,
                ImportPreviewStatus.FAILURE.value(),
                findings(result.validationFindings()),
                errors(result.processingErrors()),
                result.filesSeen(),
                result.itemsDispatched(),
                result.skipped(),
                result.processorFailures());
    }

    private static List<ImportPreviewFindingDto> findings(List<ImportPreviewFinding> findings) {
        return findings.stream()
                .map(finding -> new ImportPreviewFindingDto(
                        finding.severity(),
                        finding.message(),
                        finding.location()))
                .toList();
    }

    private static List<ImportPreviewProcessingErrorDto> errors(List<ImportPreviewProcessingError> errors) {
        return errors.stream()
                .map(error -> new ImportPreviewProcessingErrorDto(error.message(), error.location()))
                .toList();
    }
}
