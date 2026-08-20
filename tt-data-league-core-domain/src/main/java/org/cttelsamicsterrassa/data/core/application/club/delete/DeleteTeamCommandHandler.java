package org.cttelsamicsterrassa.data.core.application.club.delete;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class DeleteTeamCommandHandler extends DomainCommandHandler<DeleteTeamCommand> {

    private final TeamRepository teamRepository;

    @Inject
    public DeleteTeamCommandHandler(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public DomainCommandResponse handle(DeleteTeamCommand command) {
        return teamRepository.findTeamById(command.getTeamId())
                .map(team -> {
                    team.delete();
                    teamRepository.deleteTeamById(command.getTeamId());
                    return DomainCommandResponse.successResponse(
                            String.format("Team deleted successfully: %s", command.getTeamId()));
                })
                .orElseGet(() -> DomainCommandResponse.successResponse(
                        String.format("Team not found: %s", command.getTeamId())));
    }
}
