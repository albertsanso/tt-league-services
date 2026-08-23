package org.cttelsamicsterrassa.data.api.rest.club;

import org.cttelsamicsterrassa.data.core.application.club.find.FederatedClubCompetitionDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.FederatedClubMatchReadModel;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record ClubCompetitionDetailsDto(
        UUID clubId,
        String clubName,
        String source,
        String competition,
        String season,
        List<MatchDetailsDto> matches,
        UUID canonicalClubId,
        String canonicalClubName) {

    public ClubCompetitionDetailsDto(
            UUID clubId,
            String clubName,
            String source,
            String competition,
            String season,
            List<MatchDetailsDto> matches) {
        this(clubId, clubName, source, competition, season, matches, null, null);
    }

    public static ClubCompetitionDetailsDto fromObject(FederatedClubCompetitionDetailsReadModel details) {
        return new ClubCompetitionDetailsDto(
                details.federatedClubId(),
                details.federatedClubName(),
                details.source().name(),
                details.competition(),
                details.season().toString(),
                details.matches().stream().map(MatchDetailsDto::fromObject).toList(),
                details.canonicalClubId(),
                details.canonicalClubName());
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
        private static MatchDetailsDto fromObject(FederatedClubMatchReadModel match) {
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
