package org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper;

import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.TeamJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@AllArgsConstructor
@Component
public class TeamToTeamJPAMapper implements Function<Team, TeamJPA> {

    private final FederatedClubToFederatedClubJPAMapper clubToFederatedClubJPAMapper;

    @Override
    public TeamJPA apply(Team team) {
        if (team == null) {
            return null;
        }
        TeamJPA result = new TeamJPA();
        result.setId(team.getId());
        result.setSource(team.getSource() == null ? null : Source.valueOf(team.getSource().name()));
        result.setName(team.getName());
        result.setSeason(team.getSeason().toString());
        result.setFederatedClub(team.getFederatedClub()
                .map(clubToFederatedClubJPAMapper).orElse(null));
        return result;
    }
}
