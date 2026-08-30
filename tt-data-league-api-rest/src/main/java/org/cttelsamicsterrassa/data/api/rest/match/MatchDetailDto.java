package org.cttelsamicsterrassa.data.api.rest.match;

import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchDetailReadModel;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record MatchDetailDto(UUID id, String source, String externalId, String competition, String season,
                             int groupNumber, int round, ZonedDateTime dateTime, String city, String venue,
                             TeamDto homeTeam, TeamDto awayTeam, TeamDto winnerTeam, String refereeName,
                             String refereeLicense, Integer homeGamesWon, Integer awayGamesWon, Integer homeSetsWon,
                             Integer awaySetsWon, boolean protested, List<LineupDto> lineups,
                             List<GameDto> games) {
    static MatchDetailDto from(MatchDetailReadModel value) {
        return new MatchDetailDto(value.id(), value.source() == null ? null : value.source().name(),
                value.externalId(), value.competition(), value.season() == null ? null : value.season().toString(),
                value.groupNumber(), value.round(), value.dateTime(), value.city(), value.venue(),
                team(value.homeTeam()), team(value.awayTeam()), team(value.winnerTeam()), value.refereeName(),
                value.refereeLicense(),
                value.homeGamesWon(), value.awayGamesWon(), value.homeSetsWon(), value.awaySetsWon(),
                value.protested(), value.lineups().stream().map(LineupDto::from).toList(),
                value.games().stream().map(GameDto::from).toList());
    }

    private static TeamDto team(MatchDetailReadModel.TeamReadModel value) {
        return value == null ? null : new TeamDto(value.id(), value.name(), value.source(), value.season());
    }

    public record TeamDto(UUID id, String name, String source, String season) {
    }
    public record PlayerDto(UUID playerSeasonId, UUID federatedPlayerId, UUID canonicalPlayerId,
                            String name, String license, String source, String season) {
        static PlayerDto from(MatchDetailReadModel.PlayerReadModel value) {
            return value == null ? null : new PlayerDto(value.playerSeasonId(), value.federatedPlayerId(),
                    value.canonicalPlayerId(), value.name(), value.license(), value.source(), value.season());
        }
    }
    public record LineupDto(UUID id, TeamDto team, String letter, int position, PlayerDto player, Float ranking) {
        static LineupDto from(MatchDetailReadModel.LineupReadModel value) {
            return new LineupDto(value.id(), team(value.team()), value.letter(), value.position(),
                    PlayerDto.from(value.player()), value.ranking());
        }
        private static TeamDto team(MatchDetailReadModel.TeamReadModel value) {
            return value == null ? null : new TeamDto(value.id(), value.name(), value.source(), value.season());
        }
    }
    public record SetDto(UUID id, int setNumber, int homePoints, int awayPoints) {
    }
    public record DoublesPlayerDto(UUID id, String side, PlayerDto player) {
    }
    public record GameDto(UUID id, int gameNumber, String type, String crossover, PlayerDto homePlayer,
                          PlayerDto awayPlayer, Integer homeSetsWon, Integer awaySetsWon, String winnerSide,
                          int cumulativeHomeSetsWon, int cumulativeAwaySetsWon, boolean notPlayed,
                          String reason, List<SetDto> sets, List<DoublesPlayerDto> doublesPlayers) {
        static GameDto from(MatchDetailReadModel.GameReadModel value) {
            return new GameDto(value.id(), value.gameNumber(), value.type(), value.crossover(),
                    PlayerDto.from(value.homePlayer()), PlayerDto.from(value.awayPlayer()),
                    value.homeSetsWon(), value.awaySetsWon(), value.winnerSide(),
                    value.cumulativeHomeSetsWon(), value.cumulativeAwaySetsWon(), value.notPlayed(),
                    value.reason(), value.sets().stream().map(s -> new SetDto(s.id(), s.setNumber(),
                            s.homePoints(), s.awayPoints())).toList(),
                    value.doublesPlayers().stream().map(p -> new DoublesPlayerDto(p.id(), p.side(),
                            PlayerDto.from(p.player()))).toList());
        }
    }
}
