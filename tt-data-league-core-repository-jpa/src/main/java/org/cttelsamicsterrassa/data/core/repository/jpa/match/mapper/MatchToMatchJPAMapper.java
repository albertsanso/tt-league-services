package org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.TeamToTeamJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class MatchToMatchJPAMapper implements Function<Match, MatchJPA> {

    TeamToTeamJPAMapper teamToTeamJPAMapper;

    @Override
    public MatchJPA apply(Match match) {
        if (match == null) {
            return null;
        }
        MatchJPA matchJPA = new MatchJPA();
        matchJPA.setId(match.getId());
        matchJPA.setSource(match.getSource() != null ? Source.valueOf(match.getSource().name()) : null);
        matchJPA.setExternalId(match.getExternalId());

        // The date and time are nullable in the model and absent from some match reports.
        if (match.getDateTime() != null) {
            matchJPA.setMatchDate(match.getDateTime().toLocalDate());
            matchJPA.setMatchTime(match.getDateTime().toLocalTime());
        }

        matchJPA.setCompetition(match.getCompetition());
        matchJPA.setSeason(match.getSeason() != null ? match.getSeason().toString() : null);
        matchJPA.setGroupNumber(match.getGroupNumber());
        matchJPA.setRound(match.getRound());
        matchJPA.setCity(match.getCity());
        matchJPA.setVenue(match.getVenue());
        matchJPA.setRefereeName(match.getRefereeName());
        matchJPA.setRefereeLicense(match.getRefereeLicense());
        matchJPA.setProtested(match.isProtested());

        matchJPA.setHomeTeam(teamToTeamJPAMapper.apply(match.getHomeTeam()));
        matchJPA.setAwayTeam(teamToTeamJPAMapper.apply(match.getAwayTeam()));
        matchJPA.setWinnerTeam(teamToTeamJPAMapper.apply(match.getWinnerTeam()));

        matchJPA.setHomeSetsWon(match.getHomeSetsWon());
        matchJPA.setAwaySetsWon(match.getAwaySetsWon());
        matchJPA.setHomeGamesWon(match.getHomeGamesWon());
        matchJPA.setAwayGamesWon(match.getAwayGamesWon());

        return matchJPA;
    }
}
