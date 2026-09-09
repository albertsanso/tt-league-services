package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewFindingDto;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewProcessingErrorDto;
import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportProcessResultDto;
import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportRunStatusDto;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;

import java.util.UUID;

final class ImportRunStatusDtoMapper {
    private ImportRunStatusDtoMapper() {
    }

    static ImportRunStatusDto toDto(ImportRunSnapshot snapshot) {
        ImportRunProgress progress = snapshot.progress();
        return new ImportRunStatusDto(
                snapshot.runId(),
                snapshot.importResourceId(),
                snapshot.source() == null ? null : snapshot.source().name(),
                snapshot.season(),
                snapshot.status().value(),
                progress.processed(),
                progress.total().orElse(null),
                progress.percentage().orElse(null),
                progress.skipped(),
                progress.errorCount(),
                snapshot.errorDetail().orElse(null),
                snapshot.result().map(ImportRunStatusDtoMapper::toResultDto).orElse(null));
    }

    static ImportRunStatusDto missingResource(UUID importResourceId) {
        return new ImportRunStatusDto(null, importResourceId, null, null, "failure", 0L, null, null, 0, 1,
                "Import resource not found: " + importResourceId, null);
    }

    static ImportRunStatusDto alreadyProcessing(UUID importResourceId) {
        return new ImportRunStatusDto(null, importResourceId, null, null, "failure", 0L, null, null, 0, 1,
                "Import resource is already processing: " + importResourceId, null);
    }

    static ImportRunStatusDto anotherRunActive(UUID importResourceId) {
        return new ImportRunStatusDto(null, importResourceId, null, null, "failure", 0L, null, null, 0, 1,
                "Another import is already running; only one import can run at a time.", null);
    }

    static ImportRunStatusDto missingRun(UUID runId) {
        return new ImportRunStatusDto(runId, null, null, null, "failure", 0L, null, null, 0, 1,
                "Import run not found: " + runId, null);
    }

    private static ImportProcessResultDto toResultDto(ImportProcessResult result) {
        return new ImportProcessResultDto(null, null, null, null, result.status().value(),
                result.findings().stream().map(f -> new ImportPreviewFindingDto(
                        f.severity(), f.message(), f.location())).toList(),
                result.processingErrors().stream().map(e -> new ImportPreviewProcessingErrorDto(
                        e.message(), e.location())).toList(),
                result.filesSeen(), result.itemsPersisted(), result.skipped(), result.processorFailures(),
                result.elapsedMillis(), result.persistenceWrites(), result.executionIssues(),
                result.postProcessingOutcomes());
    }
}
