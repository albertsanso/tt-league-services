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
        Integer groupNumber,
        int round,
        String phase,
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
        List<GameReadModel> games,
        TeamFormReadModel homeTeamForm,
        TeamFormReadModel awayTeamForm,
        List<PlayerFormReadModel> playerForm,
        AlignmentStabilityReadModel homeAlignmentStability,
        AlignmentStabilityReadModel awayAlignmentStability) {

    public record TeamReadModel(UUID id, String name, String source, String season, UUID clubId) {
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

    /**
     * One past match result from a team's or player's perspective, most-recent-last ordering left
     * to the caller.
     */
    public record FormResultReadModel(
            UUID matchId,
            ZonedDateTime dateTime,
            String opponent,
            String result,
            String score) {
    }

    /**
     * A team's last-N (and previous-N) match results, source+season scoped, excluding the viewed
     * match.
     */
    public record TeamFormReadModel(
            List<FormResultReadModel> lastResults,
            Double lastWinRate,
            Double previousWinRate,
            Double overallWinRate) {
    }

    /**
     * A lineup player's last-N match results, reusing the per-player match history that backs
     * {@code PlayerDetailsDto.matches[]}.
     */
    public record PlayerFormReadModel(
            UUID playerSeasonId,
            UUID canonicalPlayerId,
            List<FormResultReadModel> lastResults,
            Double winRate) {
    }

    /**
     * How often a team's exact lineup set (independent of board letter/order) has been fielded
     * together this source+season, including the viewed match, and its aggregate result.
     */
    public record AlignmentStabilityReadModel(
            int timesFielded,
            int wins,
            int draws,
            int losses,
            Double winRate,
            Double teamOverallWinRate) {
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
