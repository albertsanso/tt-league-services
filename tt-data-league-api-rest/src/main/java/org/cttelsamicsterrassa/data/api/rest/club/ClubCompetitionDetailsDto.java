package org.cttelsamicsterrassa.data.api.rest.club;

import org.cttelsamicsterrassa.data.core.application.club.find.ClubCompetitionDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.ClubMatchReadModel;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record ClubCompetitionDetailsDto(
        UUID clubId,
        String clubName,
        String source,
        String competition,
        String season,
        List<MatchDetailsDto> matches) {

    public static ClubCompetitionDetailsDto fromObject(ClubCompetitionDetailsReadModel details) {
        return new ClubCompetitionDetailsDto(
                details.clubId(),
                details.clubName(),
                details.source().name(),
                details.competition(),
                details.season().toString(),
                details.matches().stream().map(MatchDetailsDto::fromObject).toList());
    }

    public record MatchDetailsDto(
            UUID id,
            String homeTeam,
            String awayTeam,
            Integer homeGamesWon,
            Integer awayGamesWon,
            String result,
            int round,
            ZonedDateTime dateTime,
            String city,
            String venue) {
        private static MatchDetailsDto fromObject(ClubMatchReadModel match) {
            return new MatchDetailsDto(
                    match.id(),
                    match.homeTeam(),
                    match.awayTeam(),
                    match.homeGamesWon(),
                    match.awayGamesWon(),
                    match.result(),
                    match.round(),
                    match.dateTime(),
                    match.city(),
                    match.venue());
        }
    }
}
