package org.cttelsamicsterrassa.data.core.application.club.create;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class CreateClubSeasonCommandHandler extends DomainCommandHandler<CreateClubSeasonCommand> {

    private final ClubSeasonRepository clubSeasonRepository;

    @Inject
    public CreateClubSeasonCommandHandler(ClubSeasonRepository clubSeasonRepository) {
        this.clubSeasonRepository = clubSeasonRepository;
    }

    @Override
    public DomainCommandResponse handle(CreateClubSeasonCommand command) {
        return clubSeasonRepository.findClubSeasonByNameAndSeasonAndSource(command.getClubName(), command.getSeason(), null)
                .map(existingClubSeason ->
                        DomainCommandResponse.failResponse("Club season with the same name and season already exists"))
                .orElseGet(() -> {
                    var newClubSeason = ClubSeason.createNew(null, command.getClubName(), command.getSeason(), command.getClub());
                    clubSeasonRepository.saveClubSeason(newClubSeason);
                    return DomainCommandResponse.successResponse(newClubSeason);
                });
    }
}
