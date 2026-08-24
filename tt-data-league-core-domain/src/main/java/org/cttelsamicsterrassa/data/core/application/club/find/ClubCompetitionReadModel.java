package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.Objects;

public record ClubCompetitionReadModel(
        String name,
        ImportSource source,
        Season season,
        int matchCount,
        int wins,
        int draws,
        int losses) {
    public ClubCompetitionReadModel {
        Objects.requireNonNull(season, "season must not be null");
        if (matchCount < 0 || wins < 0 || draws < 0 || losses < 0) {
            throw new IllegalArgumentException("Competition totals must not be negative");
        }
    }
}
