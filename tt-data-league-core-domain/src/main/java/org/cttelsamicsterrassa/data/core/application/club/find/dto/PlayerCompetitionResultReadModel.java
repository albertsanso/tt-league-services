package org.cttelsamicsterrassa.data.core.application.club.find.dto;

import java.util.Objects;

public record PlayerCompetitionResultReadModel(
        String competition,
        int matchCount,
        int wins,
        int draws,
        int losses) {

    public PlayerCompetitionResultReadModel {
        Objects.requireNonNull(competition, "competition must not be null");
        if (matchCount < 0 || wins < 0 || draws < 0 || losses < 0) {
            throw new IllegalArgumentException("Player competition result totals must not be negative");
        }
    }
}
