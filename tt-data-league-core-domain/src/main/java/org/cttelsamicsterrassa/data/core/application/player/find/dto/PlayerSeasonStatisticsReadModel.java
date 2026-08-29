package org.cttelsamicsterrassa.data.core.application.player.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

public record PlayerSeasonStatisticsReadModel(
        ImportSource source,
        Season season,
        int matchesPlayed,
        int wins,
        int losses,
        Double winPercentage,
        Double averageScore) {
}
