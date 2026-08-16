package org.cttelsamicsterrassa.data.core.application.club.delete;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class DeleteClubCommandHandler extends DomainCommandHandler<DeleteClubCommand> {

    private final ClubRepository clubRepository;

    @Inject
    public DeleteClubCommandHandler(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    @Override
    public DomainCommandResponse handle(DeleteClubCommand command) {

        return clubRepository.findClubById(command.getClubId())
                .map(existingClub -> {
                            existingClub.delete();
                            clubRepository.deleteClubById(command.getClubId());
                            return DomainCommandResponse.successResponse(String.format("Club deleted successfully: %s", command.getClubId()));
                        })
                .orElseGet(() -> {
                    return DomainCommandResponse.successResponse(String.format("Club not found: %s", command.getClubId()));
                });

    }
}
