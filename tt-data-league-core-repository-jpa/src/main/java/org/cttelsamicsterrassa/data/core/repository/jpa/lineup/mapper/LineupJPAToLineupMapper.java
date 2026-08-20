package org.cttelsamicsterrassa.data.core.repository.jpa.lineup.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.TeamJPAToTeamMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.lineup.model.LineupJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.mapper.MatchJPAToMatchMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerSeasonJPAToPlayerSeasonMapper;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class LineupJPAToLineupMapper implements Function<LineupJPA, Lineup> {

    MatchJPAToMatchMapper matchJPAToMatchMapper;
    PlayerSeasonJPAToPlayerSeasonMapper playerSeasonJPAToPlayerSeasonMapper;
    TeamJPAToTeamMapper teamJPAToTeamMapper;

    @Override
    public Lineup apply(LineupJPA lineupJPA) {
        if (lineupJPA == null) {
            return null;
        }
        return Lineup.builder()
                .id(lineupJPA.getId())
                .source(lineupJPA.getSource() == null ? null : ImportSource.valueOf(lineupJPA.getSource().name()))
                .position(lineupJPA.getPosition())
                .letter(lineupJPA.getLetter())
                // Most match reports carry no ranking at all, so the column is genuinely empty.
                .ranking(lineupJPA.getRanking() != null ? lineupJPA.getRanking().floatValue() : null)
                .player(playerSeasonJPAToPlayerSeasonMapper.apply(lineupJPA.getPlayer()))
                .match(matchJPAToMatchMapper.apply(lineupJPA.getMatch()))
                .team(teamJPAToTeamMapper.apply(lineupJPA.getTeam()))
                .createExisting();
    }
}
