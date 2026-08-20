package org.cttelsamicsterrassa.data.core.repository.jpa.lineup.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.TeamToTeamJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.lineup.model.LineupJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper.MatchToMatchJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerSeasonToPlayerSeasonJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.function.Function;

@AllArgsConstructor
@Component
public class LineupToLineupJPAMapper implements Function<Lineup, LineupJPA> {

    TeamToTeamJPAMapper teamToTeamJPAMapper;
    PlayerSeasonToPlayerSeasonJPAMapper playerSeasonToPlayerSeasonJPAMapper;
    MatchToMatchJPAMapper matchToMatchJPAMapper;

    @Override
    public LineupJPA apply(Lineup lineup) {
        if (lineup == null) {
            return null;
        }
        LineupJPA lineupJPA = new LineupJPA();
        lineupJPA.setId(lineup.getId());
        lineupJPA.setSource(lineup.getSource() == null ? null : Source.valueOf(lineup.getSource().name()));
        lineupJPA.setPlayer(playerSeasonToPlayerSeasonJPAMapper.apply(lineup.getPlayer()));
        lineupJPA.setTeam(teamToTeamJPAMapper.apply(lineup.getTeam()));
        lineupJPA.setMatch(matchToMatchJPAMapper.apply(lineup.getMatch()));
        lineupJPA.setLetter(lineup.getLetter());
        lineupJPA.setPosition(lineup.getPosition());
        lineupJPA.setRanking(lineup.getRanking() != null ? BigDecimal.valueOf(lineup.getRanking()) : null);
        return lineupJPA;
    }
}
