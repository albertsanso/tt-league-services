package org.cttelsamicsterrassa.data.core.application.match.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.List;

public record MatchSearchReadModel(
        UUID id,
        ImportSource source,
        String competition,
        Season season,
        int round,
        ZonedDateTime dateTime,
        String homeTeam,
        String awayTeam,
        String winnerTeam,
        Integer homeGamesWon,
        Integer awayGamesWon,
        Integer homeSetsWon,
        Integer awaySetsWon,
        boolean protested,
        List<PlayerReadModel> homePlayers,
        List<PlayerReadModel> awayPlayers) {
    public record PlayerReadModel(UUID id, String name, String license) {
    }
}
