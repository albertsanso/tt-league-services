package org.cttelsamicsterrassa.data.api.rest.match;

import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchDetailReadModel;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record MatchDetailDto(UUID id, String source, String externalId, String competition, String season,
                             Integer groupNumber, int round, String phase, ZonedDateTime dateTime, String city,
                             String venue, TeamDto homeTeam, TeamDto awayTeam, TeamDto winnerTeam,
                             String refereeName, String refereeLicense, Integer homeGamesWon,
                             Integer awayGamesWon, Integer homeSetsWon,
                             Integer awaySetsWon, boolean protested, List<LineupDto> lineups,
                             List<GameDto> games, TeamFormDto homeTeamForm, TeamFormDto awayTeamForm,
                             List<PlayerFormDto> playerForm, AlignmentStabilityDto homeAlignmentStability,
                             AlignmentStabilityDto awayAlignmentStability) {
    static MatchDetailDto from(MatchDetailReadModel value) {
        return new MatchDetailDto(value.id(), value.source() == null ? null : value.source().name(),
                value.externalId(), value.competition(), value.season() == null ? null : value.season().toString(),
                value.groupNumber(), value.round(), value.phase(), value.dateTime(), value.city(), value.venue(),
                team(value.homeTeam()), team(value.awayTeam()), team(value.winnerTeam()), value.refereeName(),
                value.refereeLicense(),
                value.homeGamesWon(), value.awayGamesWon(), value.homeSetsWon(), value.awaySetsWon(),
                value.protested(), value.lineups().stream().map(LineupDto::from).toList(),
                value.games().stream().map(GameDto::from).toList(),
                TeamFormDto.from(value.homeTeamForm()), TeamFormDto.from(value.awayTeamForm()),
                value.playerForm().stream().map(PlayerFormDto::from).toList(),
                AlignmentStabilityDto.from(value.homeAlignmentStability()),
                AlignmentStabilityDto.from(value.awayAlignmentStability()));
    }

    private static TeamDto team(MatchDetailReadModel.TeamReadModel value) {
        return value == null ? null : new TeamDto(value.id(), value.name(), value.source(), value.season());
    }

    public record TeamDto(UUID id, String name, String source, String season) {
    }
    public record FormResultDto(UUID matchId, ZonedDateTime dateTime, String opponent, String result, String score) {
        static FormResultDto from(MatchDetailReadModel.FormResultReadModel value) {
            return new FormResultDto(value.matchId(), value.dateTime(), value.opponent(), value.result(),
                    value.score());
        }
    }
    public record TeamFormDto(List<FormResultDto> lastResults, Double lastWinRate, Double previousWinRate,
                              Double overallWinRate) {
        static TeamFormDto from(MatchDetailReadModel.TeamFormReadModel value) {
            if (value == null) {
                return null;
            }
            return new TeamFormDto(value.lastResults().stream().map(FormResultDto::from).toList(),
                    value.lastWinRate(), value.previousWinRate(), value.overallWinRate());
        }
    }
    public record PlayerFormDto(UUID playerSeasonId, UUID canonicalPlayerId, List<FormResultDto> lastResults,
                                Double winRate) {
        static PlayerFormDto from(MatchDetailReadModel.PlayerFormReadModel value) {
            return new PlayerFormDto(value.playerSeasonId(), value.canonicalPlayerId(),
                    value.lastResults().stream().map(FormResultDto::from).toList(), value.winRate());
        }
    }
    public record AlignmentStabilityDto(int timesFielded, int wins, int draws, int losses, Double winRate,
                                        Double teamOverallWinRate) {
        static AlignmentStabilityDto from(MatchDetailReadModel.AlignmentStabilityReadModel value) {
            if (value == null) {
                return null;
            }
            return new AlignmentStabilityDto(value.timesFielded(), value.wins(), value.draws(), value.losses(),
                    value.winRate(), value.teamOverallWinRate());
        }
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
