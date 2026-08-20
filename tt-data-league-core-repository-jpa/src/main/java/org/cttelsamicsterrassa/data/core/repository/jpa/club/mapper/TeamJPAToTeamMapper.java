package org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.TeamJPA;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class TeamJPAToTeamMapper implements Function<TeamJPA, Team> {

    private final ClubJPAToClubMapper clubJPAToClubMapper;

    @Override
    public Team apply(TeamJPA teamJPA) {
        if (teamJPA == null) {
            return null;
        }
        return Team.createExisting(
                teamJPA.getId(),
                teamJPA.getSource() == null ? null : ImportSource.valueOf(teamJPA.getSource().name()),
                teamJPA.getName(),
                Season.fromFormatted(teamJPA.getSeason()),
                clubJPAToClubMapper.apply(teamJPA.getClub())
        );
    }
}
