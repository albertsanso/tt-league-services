package org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.ClubSeasonJPAToClubSeasonMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.function.Function;

@AllArgsConstructor
@Component
public class MatchJPAToMatchMapper implements Function<MatchJPA, Match> {

    ClubSeasonJPAToClubSeasonMapper clubSeasonJPAToClubSeasonMapper;

    @Override
    public Match apply(MatchJPA matchJpa) {
        if (matchJpa == null) {
            return null;
        }
        return Match.builder()
                .id(matchJpa.getId())
                .source(matchJpa.getSource() != null ? ImportSource.valueOf(matchJpa.getSource().name()) : null)
                .externalId(matchJpa.getExternalId())
                .competition(matchJpa.getCompetition())
                .season(matchJpa.getSeason() != null ? Season.fromFormatted(matchJpa.getSeason()) : null)
                .groupNumber(matchJpa.getGroupNumber())
                .round(matchJpa.getRound())
                .dateTime(toDateTime(matchJpa))
                .city(matchJpa.getCity())
                .venue(matchJpa.getVenue())
                .refereeName(matchJpa.getRefereeName())
                .protested(matchJpa.isProtested())
                .homeClub(clubSeasonJPAToClubSeasonMapper.apply(matchJpa.getHomeClub()))
                .awayClub(clubSeasonJPAToClubSeasonMapper.apply(matchJpa.getAwayClub()))
                .winnerClub(clubSeasonJPAToClubSeasonMapper.apply(matchJpa.getWinnerClub()))
                .homeSetsWon(matchJpa.getHomeSetsWon())
                .awaySetsWon(matchJpa.getAwaySetsWon())
                .homeGamesWon(matchJpa.getHomeGamesWon())
                .awayGamesWon(matchJpa.getAwayGamesWon())
                .build();
    }

    private ZonedDateTime toDateTime(MatchJPA matchJpa) {
        if (matchJpa.getMatchDate() == null) {
            return null;
        }
        LocalTime time = matchJpa.getMatchTime() != null ? matchJpa.getMatchTime() : LocalTime.MIDNIGHT;
        return matchJpa.getMatchDate().atTime(time).atZone(Match.COMPETITION_ZONE);
    }
}
