package org.cttelsamicsterrassa.data.core.application.importjob.dto;

import org.cttelsamicsterrassa.data.core.application.importresource.process.dto.ImportProcessResultDto;

import java.util.UUID;

/**
 * One season of an import job. {@code importRunId} and {@code result} are {@code null} until the season's import
 * run has been registered and has ended; {@code status} uses the import run status values.
 */
public record ImportJobSeasonDto(
        String season,
        UUID importResourceId,
        UUID importRunId,
        String status,
        String errorDetail,
        ImportProcessResultDto result) {
}
