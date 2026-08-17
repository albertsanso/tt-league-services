package org.cttelsamicsterrassa.data.core.application.club.update;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ModifyClubSeasonNameCommandHandler extends DomainCommandHandler<ModifyClubSeasonNameCommand> {

    private final ClubSeasonRepository clubSeasonRepository;

    @Inject
    public ModifyClubSeasonNameCommandHandler(ClubSeasonRepository clubSeasonRepository) {
        this.clubSeasonRepository = clubSeasonRepository;
    }

    @Override
    public DomainCommandResponse handle(ModifyClubSeasonNameCommand command) {
        return clubSeasonRepository.findClubSeasonById(command.getClubSeasonId())
                .map(clubSeason -> {
                    clubSeason.modifyName(command.getName());
                    clubSeasonRepository.saveClubSeason(clubSeason);
                    return DomainCommandResponse.successResponse(clubSeason);
                })
                .orElseGet(() -> DomainCommandResponse.successResponse(
                        String.format("Club season not found: %s", command.getClubSeasonId())));
    }
}
