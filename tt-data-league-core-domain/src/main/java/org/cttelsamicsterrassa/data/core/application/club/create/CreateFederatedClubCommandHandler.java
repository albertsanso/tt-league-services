package org.cttelsamicsterrassa.data.core.application.club.create;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class CreateFederatedClubCommandHandler extends DomainCommandHandler<CreateFederatedClubCommand> {

    private final FederatedClubRepository clubRepository;

    @Inject
    public CreateFederatedClubCommandHandler(FederatedClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainCommandResponse handle(CreateFederatedClubCommand createClubCommand) {
        if (createClubCommand.getSource() == null) {
            return DomainCommandResponse.failResponse("Club source must not be null");
        }

        return clubRepository.findFederatedClubBySourceAndName(
                    createClubCommand.getSource(), createClubCommand.getFederatedClubName())
            .map(existingClub ->
                    DomainCommandResponse.failResponse("Club with the same name already exists"))
            .orElseGet(() -> {
                var newClub = FederatedClub.createNew(
                        createClubCommand.getSource(), createClubCommand.getFederatedClubName());
                clubRepository.saveFederatedClub(newClub);
                return DomainCommandResponse.successResponse(newClub);
            });
    }
}
