package org.cttelsamicsterrassa.data.core.application.club.update;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ModifyTeamNameCommandHandler extends DomainCommandHandler<ModifyTeamNameCommand> {

    private final TeamRepository teamRepository;

    @Inject
    public ModifyTeamNameCommandHandler(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public DomainCommandResponse handle(ModifyTeamNameCommand command) {
        return teamRepository.findTeamById(command.getTeamId())
                .map(team -> {
                    team.modifyName(command.getName());
                    teamRepository.saveTeam(team);
                    return DomainCommandResponse.successResponse(team);
                })
                .orElseGet(() -> DomainCommandResponse.successResponse(
                        String.format("Team not found: %s", command.getTeamId())));
    }
}
