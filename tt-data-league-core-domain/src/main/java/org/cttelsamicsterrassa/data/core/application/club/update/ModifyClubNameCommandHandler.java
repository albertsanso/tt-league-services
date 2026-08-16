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
        return clubRepository.findClubById(modifyClubNameCommand.getClubId())
                .map(existingClub -> {
                    existingClub.modifyName(modifyClubNameCommand.getClubName());
                    clubRepository.saveClub(existingClub);
                    return DomainCommandResponse.successResponse(existingClub);
                })
                .orElseGet(() -> {
                    return DomainCommandResponse.successResponse(String.format("Club not found: %s", modifyClubNameCommand.getClubId()));
                });
    }
}
