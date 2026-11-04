package org.cttelsamicsterrassa.data.core.repository.jpa.load.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJob;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.model.ImportJobJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.model.ImportJobSeasonJPA;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Component
@AllArgsConstructor
public class ImportJobJPAToImportJobMapper implements Function<ImportJobJPA, ImportJob> {
    private final ImportProcessResultJsonCodec resultCodec;

    @Override
    public ImportJob apply(ImportJobJPA jpa) {
        if (jpa == null) {
            return null;
        }
        return ImportJob.createExisting(
                jpa.getId(),
                ImportSource.valueOf(jpa.getSource().name()),
                List.of(jpa.getSeasons().split(ImportJobToImportJobJPAMapper.SEASON_SEPARATOR)),
                jpa.getMode(),
                Optional.ofNullable(jpa.getContentSha256()),
                Optional.ofNullable(jpa.getClientRunId()),
                Optional.ofNullable(jpa.getManifestRunId()),
                jpa.isAllowPublishedShrink(),
                Path.of(jpa.getStagedZipPath()),
                jpa.getRequestedBy(),
                jpa.getStatus(),
                Optional.ofNullable(jpa.getErrorDetail()),
                jpa.getCreatedAt(),
                Optional.ofNullable(jpa.getStartedAt()),
                Optional.ofNullable(jpa.getFinishedAt()),
                jpa.getSeasonResults().stream().map(season -> toSeason(jpa, season)).toList());
    }

    private ImportJobSeason toSeason(ImportJobJPA job, ImportJobSeasonJPA season) {
        try {
            return ImportJobSeason.createExisting(
                    season.getId(),
                    season.getSeason(),
                    season.getImportResourceId(),
                    Optional.ofNullable(season.getImportRunId()),
                    season.getStatus(),
                    Optional.ofNullable(season.getResultJson()).map(resultCodec::fromJson),
                    Optional.ofNullable(season.getErrorDetail()));
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Unable to read season " + season.getSeason() + " of import job "
                    + job.getId(), exception);
        }
    }
}
