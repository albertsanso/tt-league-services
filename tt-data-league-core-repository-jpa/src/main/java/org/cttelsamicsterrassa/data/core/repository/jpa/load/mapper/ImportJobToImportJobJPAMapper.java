package org.cttelsamicsterrassa.data.core.repository.jpa.load.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJob;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobSeason;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.model.ImportJobJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.load.model.ImportJobSeasonJPA;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
@AllArgsConstructor
public class ImportJobToImportJobJPAMapper implements Function<ImportJob, ImportJobJPA> {
    static final String SEASON_SEPARATOR = ",";

    private final ImportProcessResultJsonCodec resultCodec;

    @Override
    public ImportJobJPA apply(ImportJob job) {
        if (job == null) {
            return null;
        }
        ImportJobJPA jpa = new ImportJobJPA(
                job.getId(),
                Source.valueOf(job.getSource().name()),
                String.join(SEASON_SEPARATOR, job.getSeasons()),
                job.getMode(),
                job.getContentSha256().orElse(null),
                job.getClientRunId().orElse(null),
                job.getManifestRunId().orElse(null),
                job.isAllowPublishedShrink(),
                job.getStagedZipPath().toString(),
                job.getRequestedBy(),
                job.getStatus(),
                job.getErrorDetail().orElse(null),
                job.getCreatedAt(),
                job.getStartedAt().orElse(null),
                job.getFinishedAt().orElse(null),
                new ArrayList<>());
        List<ImportJobSeason> seasons = job.getSeasonResults();
        for (int position = 0; position < seasons.size(); position++) {
            ImportJobSeason season = seasons.get(position);
            jpa.getSeasonResults().add(new ImportJobSeasonJPA(
                    season.getId(),
                    jpa,
                    position,
                    season.getSeason(),
                    season.getImportResourceId(),
                    season.getImportRunId().orElse(null),
                    season.getStatus(),
                    season.getErrorDetail().orElse(null),
                    season.getResult().map(resultCodec::toJson).orElse(null)));
        }
        return jpa;
    }
}
