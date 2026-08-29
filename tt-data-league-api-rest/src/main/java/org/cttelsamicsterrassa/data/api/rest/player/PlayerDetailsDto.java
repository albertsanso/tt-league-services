package org.cttelsamicsterrassa.data.api.rest.player;

import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record PlayerDetailsDto(
        UUID id,
        String name,
        List<FederatedDto> federatedPlayers,
        List<RegistrationDto> registrations,
        List<ClubDto> clubs,
        List<CompetitionDto> competitions,
        List<MatchDto> matches,
        List<StatisticsDto> statistics) {
    public static PlayerDetailsDto fromObject(PlayerDetailsReadModel details) {
        return new PlayerDetailsDto(details.id(), details.name(),
                details.federatedPlayers().stream()
                        .map(value -> new FederatedDto(value.id(), value.name(), value.license(),
                                value.source() == null ? null : value.source().name())).toList(),
                details.registrations().stream()
                        .map(value -> new RegistrationDto(value.id(), value.name(), value.license(),
                                value.season() == null ? null : value.season().toString(),
                                value.source() == null ? null : value.source().name(), value.federatedPlayerId())).toList(),
                details.clubs().stream()
                        .map(value -> new ClubDto(value.id(), value.name(),
                                value.source() == null ? null : value.source().name(),
                                value.season() == null ? null : value.season().toString())).toList(),
                details.competitions().stream()
                        .map(value -> new CompetitionDto(value.name(),
                                value.source() == null ? null : value.source().name(),
                                value.season() == null ? null : value.season().toString(), value.matchCount())).toList(),
                details.matches().stream().map(value -> new MatchDto(value.id(),
                        value.source() == null ? null : value.source().name(), value.competition(),
                        value.season() == null ? null : value.season().toString(), value.round(),
                        value.dateTime(), value.homeTeam(), value.awayTeam(), value.homeGamesWon(),
                        value.awayGamesWon(), value.result(), value.playerGamesWon(), value.playerTeam())).toList(),
                details.statistics().stream().map(value -> new StatisticsDto(
                        value.source() == null ? null : value.source().name(),
                        value.season() == null ? null : value.season().toString(),
                        value.matchesPlayed(), value.wins(), value.losses(),
                        value.winPercentage(), value.averageScore())).toList());
    }

    public record FederatedDto(UUID id, String name, String license, String source) {
    }

    public record RegistrationDto(
            UUID id, String name, String license, String season, String source, UUID federatedPlayerId) {
    }

    public record ClubDto(UUID id, String name, String source, String season) {
    }

    public record CompetitionDto(String name, String source, String season, int matchCount) {
    }

    public record MatchDto(
            UUID id, String source, String competition, String season, int round, ZonedDateTime dateTime,
            String homeTeam, String awayTeam, Integer homeGamesWon, Integer awayGamesWon, String result,
            Integer playerGamesWon, String playerTeam) {
        public MatchDto(UUID id, String source, String competition, String season, int round, ZonedDateTime dateTime,
                        String homeTeam, String awayTeam, Integer homeGamesWon, Integer awayGamesWon, String result) {
            this(id, source, competition, season, round, dateTime, homeTeam, awayTeam, homeGamesWon, awayGamesWon,
                    result, null, null);
        }

        public MatchDto(UUID id, String source, String competition, String season, int round, ZonedDateTime dateTime,
                        String homeTeam, String awayTeam, Integer homeGamesWon, Integer awayGamesWon, String result,
                        Integer playerGamesWon) {
            this(id, source, competition, season, round, dateTime, homeTeam, awayTeam, homeGamesWon, awayGamesWon,
                    result, playerGamesWon, null);
        }
    }

    public record StatisticsDto(
            @Schema(description = "Federation source; null means data without a source scope")
            String source,
            @Schema(description = "Season in YYYY-YYYY format; null means all or unknown season")
            String season,
            @Schema(description = "Number of matches played")
            int matchesPlayed,
            @Schema(description = "Matches won")
            int wins,
            @Schema(description = "Matches lost")
            int losses,
            @Schema(description = "Percentage of decided matches won, or null when no result is available")
            Double winPercentage,
            @Schema(description = "Average games won per match, or null when scores are unavailable")
            Double averageScore) {
    }
}
