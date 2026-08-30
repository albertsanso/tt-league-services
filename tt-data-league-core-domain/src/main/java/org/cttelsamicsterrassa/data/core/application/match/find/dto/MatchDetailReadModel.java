package org.cttelsamicsterrassa.data.core.application.match.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record MatchDetailReadModel(
        UUID id,
        ImportSource source,
        String externalId,
        String competition,
        Season season,
        int groupNumber,
        int round,
        ZonedDateTime dateTime,
        String city,
        String venue,
        TeamReadModel homeTeam,
        TeamReadModel awayTeam,
        TeamReadModel winnerTeam,
        String refereeName,
        String refereeLicense,
        Integer homeGamesWon,
        Integer awayGamesWon,
        Integer homeSetsWon,
        Integer awaySetsWon,
        boolean protested,
        List<LineupReadModel> lineups,
        List<GameReadModel> games) {

    public record TeamReadModel(UUID id, String name, String source, String season) {
    }

    public record PlayerReadModel(
            UUID playerSeasonId,
            UUID federatedPlayerId,
            UUID canonicalPlayerId,
            String name,
            String license,
            String source,
            String season) {
    }

    public record LineupReadModel(
            UUID id,
            TeamReadModel team,
            String letter,
            int position,
            PlayerReadModel player,
            Float ranking) {
    }

    public record SetReadModel(UUID id, int setNumber, int homePoints, int awayPoints) {
    }

    public record DoublesPlayerReadModel(UUID id, String side, PlayerReadModel player) {
    }

    public record GameReadModel(
            UUID id,
            int gameNumber,
            String type,
            String crossover,
            PlayerReadModel homePlayer,
            PlayerReadModel awayPlayer,
            Integer homeSetsWon,
            Integer awaySetsWon,
            String winnerSide,
            int cumulativeHomeSetsWon,
            int cumulativeAwaySetsWon,
            boolean notPlayed,
            String reason,
            List<SetReadModel> sets,
            List<DoublesPlayerReadModel> doublesPlayers) {
    }
}
