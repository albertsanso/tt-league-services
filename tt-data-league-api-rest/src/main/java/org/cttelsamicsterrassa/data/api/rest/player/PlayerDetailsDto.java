package org.cttelsamicsterrassa.data.api.rest.player;

import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;

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
        List<MatchDto> matches) {
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
                        value.awayGamesWon(), value.result())).toList());
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
            String homeTeam, String awayTeam, Integer homeGamesWon, Integer awayGamesWon, String result) {
    }
}
