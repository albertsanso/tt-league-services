package org.cttelsamicsterrassa.data.api.rest.club;

import org.cttelsamicsterrassa.data.core.application.club.find.FederatedClubCompetitionReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.FederatedClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.FederatedClubPlayerReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.FederatedClubTeamReadModel;

import java.util.List;
import java.util.UUID;

public record ClubDetailsDto(
        UUID id,
        String name,
        String source,
        List<TeamDetailsDto> teams,
        List<CompetitionDetailsDto> competitions,
        List<PlayerDetailsDto> players) {

    public ClubDetailsDto(
            UUID id,
            String name,
            String source,
            List<TeamDetailsDto> teams,
            List<CompetitionDetailsDto> competitions) {
        this(id, name, source, teams, competitions, List.of());
    }

    public static ClubDetailsDto fromObject(FederatedClubDetailsReadModel details) {
        return new ClubDetailsDto(
                details.id(),
                details.name(),
                details.source().name(),
                details.teams().stream().map(TeamDetailsDto::fromObject).toList(),
                details.competitions().stream().map(CompetitionDetailsDto::fromObject).toList(),
                details.players().stream().map(PlayerDetailsDto::fromObject).toList());
    }

    public record TeamDetailsDto(UUID id, String name, String source, String season) {
        private static TeamDetailsDto fromObject(FederatedClubTeamReadModel team) {
            return new TeamDetailsDto(
                    team.id(),
                    team.name(),
                    team.source() == null ? null : team.source().name(),
                    team.season() == null ? null : team.season().toString());
        }
    }

    public record CompetitionDetailsDto(
            String name,
            String season,
            int matchCount,
            ResultTotalsDto resultTotals) {
        private static CompetitionDetailsDto fromObject(FederatedClubCompetitionReadModel competition) {
            return new CompetitionDetailsDto(
                    competition.name(),
                    competition.season().toString(),
                    competition.matchCount(),
                    new ResultTotalsDto(
                            competition.wins(),
                            competition.draws(),
                            competition.losses()));
        }
    }

    public record ResultTotalsDto(int wins, int draws, int losses) {
    }

    public record PlayerDetailsDto(
            UUID playerSeasonId,
            UUID playerId,
            String playerName,
            String registrationName,
            String license,
            String source,
            String season,
            List<String> competitions) {
        public PlayerDetailsDto(
                UUID playerSeasonId,
                UUID playerId,
                String playerName,
                String registrationName,
                String license,
                String source,
                String season) {
            this(playerSeasonId, playerId, playerName, registrationName, license, source, season, List.of());
        }

        private static PlayerDetailsDto fromObject(FederatedClubPlayerReadModel player) {
            return new PlayerDetailsDto(
                    player.playerSeasonId(),
                    player.federatedPlayerId(),
                    player.federatedPlayerName(),
                    player.registrationName(),
                    player.license(),
                    player.source().name(),
                    player.season().toString(),
                    player.competitions());
        }
    }
}
