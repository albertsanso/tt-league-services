package org.cttelsamicsterrassa.data.core.application.club.update;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ModifyFederatedClubNameCommandHandler extends DomainCommandHandler<ModifyFederatedClubNameCommand> {

    private final FederatedClubRepository clubRepository;

    @Inject
    public ModifyFederatedClubNameCommandHandler(FederatedClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainCommandResponse handle(ModifyFederatedClubNameCommand modifyClubNameCommand) {
        String clubName = modifyClubNameCommand.getFederatedClubName();
        if (clubName == null || clubName.trim().length() < 2) {
            return DomainCommandResponse.failResponse("Club name must contain at least 2 characters");
        }
        return clubRepository.findFederatedClubById(modifyClubNameCommand.getFederatedClubId())
                .map(existingClub -> {
                    if (modifyClubNameCommand.getSource() != null
                            && modifyClubNameCommand.getSource() != existingClub.getSource()) {
                        return DomainCommandResponse.failResponse("Club source does not match");
                    }
                    existingClub.modifyName(clubName.trim());
                    clubRepository.saveFederatedClub(existingClub);
                    return DomainCommandResponse.successResponse(existingClub);
                })
                .orElseGet(() -> DomainCommandResponse.failResponse(
                        String.format("Club not found: %s", modifyClubNameCommand.getFederatedClubId())));
    }
}
