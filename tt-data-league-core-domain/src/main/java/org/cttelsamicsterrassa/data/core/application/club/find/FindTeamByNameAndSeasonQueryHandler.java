package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindTeamByNameAndSeasonQueryHandler extends DomainQueryHandler<FindTeamByNameAndSeasonQuery, Team> {

    private final TeamRepository teamRepository;

    @Inject
    public FindTeamByNameAndSeasonQueryHandler(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public DomainQueryResponse<Team> handle(FindTeamByNameAndSeasonQuery query) {
        return teamRepository.findTeamByNameAndSeasonAndSource(query.getTeamName(), query.getSeason(), null)
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
