package org.cttelsamicsterrassa.data.core.application.club.consolidate;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Named
public class ConsolidateClubsCommandHandler extends DomainCommandHandler<ConsolidateClubsCommand> {

    private final ClubRepository clubRepository;
    private final FederatedClubRepository federatedClubRepository;

    @Inject
    public ConsolidateClubsCommandHandler(ClubRepository clubRepository, FederatedClubRepository federatedClubRepository) {
        this.clubRepository = clubRepository;
        this.federatedClubRepository = federatedClubRepository;
    }

    @Override
    public DomainCommandResponse handle(ConsolidateClubsCommand command) {
        LinkedHashSet<UUID> distinctClubIds = new LinkedHashSet<>(command.getClubIds());
        if (distinctClubIds.size() < 2) {
            return DomainCommandResponse.failResponse("At least two distinct clubs must be selected for consolidation");
        }

        UUID primaryClubId = command.getPrimaryClubId();
        if (primaryClubId == null || !distinctClubIds.contains(primaryClubId)) {
            return DomainCommandResponse.failResponse("Primary club must be one of the selected clubs");
        }

        String canonicalName = command.getCanonicalName();
        if (canonicalName == null || canonicalName.trim().length() < 2) {
            return DomainCommandResponse.failResponse("Canonical name must contain at least 2 characters");
        }

        Optional<DomainCommandResponse> missingClub = findMissingClub(distinctClubIds);
        if (missingClub.isPresent()) {
            return missingClub.get();
        }

        Club primaryClub = clubRepository.findClubById(primaryClubId).orElseThrow();
        List<UUID> nonPrimaryClubIds = distinctClubIds.stream()
                .filter(id -> !id.equals(primaryClubId))
                .toList();

        for (UUID nonPrimaryClubId : nonPrimaryClubIds) {
            reassignFederatedClubs(nonPrimaryClubId, primaryClub);
            clubRepository.deleteClubById(nonPrimaryClubId);
        }

        primaryClub.modifyName(canonicalName.trim());
        primaryClub.consolidate(nonPrimaryClubIds);
        clubRepository.saveClub(primaryClub);

        return DomainCommandResponse.successResponse(primaryClub);
    }

    private Optional<DomainCommandResponse> findMissingClub(LinkedHashSet<UUID> clubIds) {
        return clubIds.stream()
                .filter(id -> clubRepository.findClubById(id).isEmpty())
                .map(id -> DomainCommandResponse.failResponse("Club not found: " + id))
                .findFirst();
    }

    private void reassignFederatedClubs(UUID nonPrimaryClubId, Club primaryClub) {
        List<FederatedClub> federatedClubs = federatedClubRepository.findAllFederatedClubsByClubId(nonPrimaryClubId);
        for (FederatedClub federatedClub : federatedClubs) {
            federatedClubRepository.saveFederatedClub(federatedClub.withClub(primaryClub));
        }
    }
}
