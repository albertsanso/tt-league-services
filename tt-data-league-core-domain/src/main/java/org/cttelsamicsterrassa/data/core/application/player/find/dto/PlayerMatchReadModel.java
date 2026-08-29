package org.cttelsamicsterrassa.data.core.application.player.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlayerMatchReadModel(
        UUID id, ImportSource source, String competition, Season season, int round, ZonedDateTime dateTime,
        String homeTeam, String awayTeam, Integer homeGamesWon, Integer awayGamesWon, String result,
        Integer playerGamesWon, String playerTeam, List<PlayerGameReadModel> games) {
    public PlayerMatchReadModel {
        games = List.copyOf(Objects.requireNonNull(games));
    }

    public PlayerMatchReadModel(UUID id, ImportSource source, String competition, Season season, int round,
                                ZonedDateTime dateTime, String homeTeam, String awayTeam, Integer homeGamesWon,
                                Integer awayGamesWon, String result) {
        this(id, source, competition, season, round, dateTime, homeTeam, awayTeam, homeGamesWon, awayGamesWon,
                result, null, null, List.of());
    }

    public PlayerMatchReadModel(UUID id, ImportSource source, String competition, Season season, int round,
                                ZonedDateTime dateTime, String homeTeam, String awayTeam, Integer homeGamesWon,
                                Integer awayGamesWon, String result, Integer playerGamesWon) {
        this(id, source, competition, season, round, dateTime, homeTeam, awayTeam, homeGamesWon, awayGamesWon,
                result, playerGamesWon, null, List.of());
    }

    public PlayerMatchReadModel(UUID id, ImportSource source, String competition, Season season, int round,
                                ZonedDateTime dateTime, String homeTeam, String awayTeam, Integer homeGamesWon,
                                Integer awayGamesWon, String result, Integer playerGamesWon, String playerTeam) {
        this(id, source, competition, season, round, dateTime, homeTeam, awayTeam, homeGamesWon, awayGamesWon,
                result, playerGamesWon, playerTeam, List.of());
    }
}
