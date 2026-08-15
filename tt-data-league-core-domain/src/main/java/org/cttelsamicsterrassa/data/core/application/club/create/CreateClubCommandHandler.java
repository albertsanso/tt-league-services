package org.cttelsamicsterrassa.data.core.application.club.create;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class CreateClubCommandHandler extends DomainCommandHandler<CreateClubCommand> {

    private final ClubRepository clubRepository;

    @Inject
    public CreateClubCommandHandler(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainCommandResponse handle(CreateClubCommand createClubCommand) {

        return clubRepository.findClubByName(createClubCommand.getClubName())
            .map(existingClub ->
                    DomainCommandResponse.failResponse("Club with the same name already exists"))
            .orElseGet(() -> {
                var newClub = Club.createNew(createClubCommand.getSource(), createClubCommand.getClubName());
                clubRepository.saveClub(newClub);
                return DomainCommandResponse.successResponse(newClub);
            });
    }
}
