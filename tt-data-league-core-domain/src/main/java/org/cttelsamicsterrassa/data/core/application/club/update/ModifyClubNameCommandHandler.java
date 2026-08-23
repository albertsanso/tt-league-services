package org.cttelsamicsterrassa.data.core.application.club.update;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ModifyClubNameCommandHandler extends DomainCommandHandler<ModifyClubNameCommand> {

    private final ClubRepository clubRepository;

    @Inject
    public ModifyClubNameCommandHandler(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainCommandResponse handle(ModifyClubNameCommand modifyClubNameCommand) {
        String clubName = modifyClubNameCommand.getClubName();
        if (clubName == null || clubName.trim().length() < 2) {
            return DomainCommandResponse.failResponse("Club name must contain at least 2 characters");
        }
        return clubRepository.findClubById(modifyClubNameCommand.getClubId())
                .map(existingClub -> {
                    if (modifyClubNameCommand.getSource() != null
                            && modifyClubNameCommand.getSource() != existingClub.getSource()) {
                        return DomainCommandResponse.failResponse("Club source does not match");
                    }
                    existingClub.modifyName(clubName.trim());
                    clubRepository.saveClub(existingClub);
                    return DomainCommandResponse.successResponse(existingClub);
                })
                .orElseGet(() -> DomainCommandResponse.failResponse(
                        String.format("Club not found: %s", modifyClubNameCommand.getClubId())));
    }
}
