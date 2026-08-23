package org.cttelsamicsterrassa.data.core.application.club.create;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class CreateFederatedClubCommandHandler extends DomainCommandHandler<CreateFederatedClubCommand> {

    private final FederatedClubRepository clubRepository;
    private final ClubRepository canonicalClubRepository;

    @Inject
    public CreateFederatedClubCommandHandler(FederatedClubRepository clubRepository,
                                             ClubRepository canonicalClubRepository) {
        this.clubRepository = clubRepository;
        this.canonicalClubRepository = canonicalClubRepository;
    }

    public CreateFederatedClubCommandHandler(FederatedClubRepository clubRepository) {
        this.clubRepository = clubRepository;
        this.canonicalClubRepository = null;
    }

    @Override
    public DomainCommandResponse handle(CreateFederatedClubCommand createClubCommand) {
        if (createClubCommand.getSource() == null) {
            return DomainCommandResponse.failResponse("Club source must not be null");
        }
        if (createClubCommand.getFederatedClubName() == null
                || createClubCommand.getFederatedClubName().isBlank()) {
            return DomainCommandResponse.failResponse("Club name must not be blank");
        }

        return clubRepository.findFederatedClubBySourceAndName(
                    createClubCommand.getSource(), createClubCommand.getFederatedClubName())
            .map(existingClub ->
                    DomainCommandResponse.failResponse("Club with the same name already exists"))
            .orElseGet(() -> {
                Club canonicalClub = canonicalClubRepository == null
                        ? null
                        : canonicalClubRepository.findClubByExactName(createClubCommand.getFederatedClubName())
                                .orElseGet(() -> {
                                    Club created = Club.createNew(createClubCommand.getFederatedClubName());
                                    canonicalClubRepository.saveClub(created);
                                    return created;
                                });
                var newClub = FederatedClub.createNew(
                        createClubCommand.getSource(), createClubCommand.getFederatedClubName(), canonicalClub);
                clubRepository.saveFederatedClub(newClub);
                return DomainCommandResponse.successResponse(newClub);
            });
    }
}
