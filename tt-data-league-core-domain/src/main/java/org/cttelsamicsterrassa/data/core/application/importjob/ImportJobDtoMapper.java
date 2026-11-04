package org.cttelsamicsterrassa.data.core.application.importjob;

import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobDto;
import org.cttelsamicsterrassa.data.core.application.importjob.dto.ImportJobSeasonDto;
import org.cttelsamicsterrassa.data.core.application.importresource.process.ImportProcessResultDtoMapper;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJob;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobSeason;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;

final class ImportJobDtoMapper {
    private ImportJobDtoMapper() {
    }

    static ImportJobDto toDto(ImportJob job) {
        return new ImportJobDto(job.getId(), job.getStatus().name(), job.getSource().name(), job.getSeasons(),
                job.getMode().name(), job.getContentSha256().orElse(null), job.getClientRunId().orElse(null),
                job.getManifestRunId().orElse(null), job.isAllowPublishedShrink(), job.getRequestedBy(),
                job.getErrorDetail().orElse(null), job.getCreatedAt(), job.getStartedAt().orElse(null),
                job.getFinishedAt().orElse(null),
                job.getSeasonResults().stream().map(season -> toDto(job, season)).toList());
    }

    private static ImportJobSeasonDto toDto(ImportJob job, ImportJobSeason season) {
        return new ImportJobSeasonDto(season.getSeason(), season.getImportResourceId(),
                season.getImportRunId().orElse(null), season.getStatus().value(),
                season.getErrorDetail().orElse(null),
                season.getResult().map(result -> ImportProcessResultDtoMapper.toDto(season.getImportResourceId(),
                        job.getSource().name(), season.getSeason(), ResourceType.ACTAS.name(), result))
                        .orElse(null));
    }
}
