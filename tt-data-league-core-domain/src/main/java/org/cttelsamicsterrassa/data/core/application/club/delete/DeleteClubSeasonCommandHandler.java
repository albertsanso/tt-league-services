package org.cttelsamicsterrassa.data.core.application.club.delete;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class DeleteClubSeasonCommandHandler extends DomainCommandHandler<DeleteClubSeasonCommand> {

    private final ClubSeasonRepository clubSeasonRepository;

    @Inject
    public DeleteClubSeasonCommandHandler(ClubSeasonRepository clubSeasonRepository) {
        this.clubSeasonRepository = clubSeasonRepository;
    }

    @Override
    public DomainCommandResponse handle(DeleteClubSeasonCommand command) {
        return clubSeasonRepository.findClubSeasonById(command.getClubSeasonId())
                .map(clubSeason -> {
                    clubSeason.delete();
                    clubSeasonRepository.deleteClubSeasonById(command.getClubSeasonId());
                    return DomainCommandResponse.successResponse(
                            String.format("Club season deleted successfully: %s", command.getClubSeasonId()));
                })
                .orElseGet(() -> DomainCommandResponse.successResponse(
                        String.format("Club season not found: %s", command.getClubSeasonId())));
    }
}
