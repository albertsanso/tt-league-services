package org.cttelsamicsterrassa.data.core.application.club.delete;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class DeleteFederatedClubCommandHandler extends DomainCommandHandler<DeleteFederatedClubCommand> {

    private final FederatedClubRepository clubRepository;

    @Inject
    public DeleteFederatedClubCommandHandler(FederatedClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainCommandResponse handle(DeleteFederatedClubCommand command) {

        return clubRepository.findFederatedClubById(command.getFederatedClubId())
                .map(existingClub -> {
                            existingClub.delete();
                            clubRepository.deleteFederatedClubById(command.getFederatedClubId());
                            return DomainCommandResponse.successResponse(
                                    String.format("Club deleted successfully: %s", command.getFederatedClubId()));
                        })
                .orElseGet(() -> {
                    return DomainCommandResponse.successResponse(
                            String.format("Club not found: %s", command.getFederatedClubId()));
                });

    }
}
