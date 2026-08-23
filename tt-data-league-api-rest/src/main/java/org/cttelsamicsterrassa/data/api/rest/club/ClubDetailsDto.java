package org.cttelsamicsterrassa.data.api.rest.club;

import org.cttelsamicsterrassa.data.core.application.club.find.ClubCompetitionReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.ClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.ClubTeamReadModel;

import java.util.List;
import java.util.UUID;

public record ClubDetailsDto(
        UUID id,
        String name,
        String source,
        List<TeamDetailsDto> teams,
        List<CompetitionDetailsDto> competitions) {

    public static ClubDetailsDto fromObject(ClubDetailsReadModel details) {
        return new ClubDetailsDto(
                details.id(),
                details.name(),
                details.source().name(),
                details.teams().stream().map(TeamDetailsDto::fromObject).toList(),
                details.competitions().stream().map(CompetitionDetailsDto::fromObject).toList());
    }

    public record TeamDetailsDto(UUID id, String name, String source, String season) {
        private static TeamDetailsDto fromObject(ClubTeamReadModel team) {
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
        private static CompetitionDetailsDto fromObject(ClubCompetitionReadModel competition) {
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
}
