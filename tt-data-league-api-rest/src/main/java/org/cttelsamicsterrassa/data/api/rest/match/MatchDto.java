package org.cttelsamicsterrassa.data.api.rest.match;

import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchDetailReadModel;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchReadModel;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record MatchDto(UUID id, String source, String competition, String season, int round,
                       ZonedDateTime dateTime, String homeTeam, String awayTeam, String winnerTeam,
                       Integer homeGamesWon, Integer awayGamesWon, Integer homeSetsWon,
                       Integer awaySetsWon, boolean protested,
                       List<PlayerDto> homePlayers, List<PlayerDto> awayPlayers) {
    static MatchDto from(MatchSearchReadModel value) {
        return new MatchDto(value.id(), name(value.source()), value.competition(),
                value.season() == null ? null : value.season().toString(), value.round(), value.dateTime(),
                value.homeTeam(), value.awayTeam(), value.winnerTeam(), value.homeGamesWon(),
                value.awayGamesWon(), value.homeSetsWon(), value.awaySetsWon(), value.protested(),
                value.homePlayers().stream().map(PlayerDto::from).toList(),
                value.awayPlayers().stream().map(PlayerDto::from).toList());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    public record PlayerDto(UUID id, String name, String license) {
        static PlayerDto from(org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchReadModel.PlayerReadModel value) {
            return new PlayerDto(value.id(), value.name(), value.license());
        }
    }
}
