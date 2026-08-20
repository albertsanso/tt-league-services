package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindTeamByIdQueryHandler extends DomainQueryHandler<FindTeamByIdQuery, Team> {

    private final TeamRepository teamRepository;

    @Inject
    public FindTeamByIdQueryHandler(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public DomainQueryResponse<Team> handle(FindTeamByIdQuery query) {
        return teamRepository.findTeamById(query.getTeamId())
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
