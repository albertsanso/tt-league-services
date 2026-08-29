package org.cttelsamicsterrassa.data.core.application.player.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.UUID;

public record PlayerMatchReadModel(
        UUID id, ImportSource source, String competition, Season season, int round, ZonedDateTime dateTime,
        String homeTeam, String awayTeam, Integer homeGamesWon, Integer awayGamesWon, String result) {
}
