package org.cttelsamicsterrassa.data.core.application.club.create;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class CreateTeamCommandHandler extends DomainCommandHandler<CreateTeamCommand> {

    private final TeamRepository teamRepository;

    @Inject
    public CreateTeamCommandHandler(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public DomainCommandResponse handle(CreateTeamCommand command) {
        return teamRepository.findTeamByNameAndSeasonAndSource(command.getClubName(), command.getSeason(), null)
                .map(existingTeam ->
                        DomainCommandResponse.failResponse("Team with the same name and season already exists"))
                .orElseGet(() -> {
                    var newTeam = Team.createNew(null, command.getClubName(), command.getSeason(), command.getClub());
                    teamRepository.saveTeam(newTeam);
                    return DomainCommandResponse.successResponse(newTeam);
                });
    }
}
