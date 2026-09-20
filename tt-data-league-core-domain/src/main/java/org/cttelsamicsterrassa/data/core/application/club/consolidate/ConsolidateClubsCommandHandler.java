package org.cttelsamicsterrassa.data.core.application.club.consolidate;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.event.DomainEvent;
import org.albertsanso.commons.event.EventBus;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClub;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Named
public class ConsolidateClubsCommandHandler extends DomainCommandHandler<ConsolidateClubsCommand> {

    private final ClubRepository clubRepository;
    private final FederatedClubRepository federatedClubRepository;
    private final EventBus eventBus;

    @Inject
    public ConsolidateClubsCommandHandler(
            ClubRepository clubRepository, FederatedClubRepository federatedClubRepository, EventBus eventBus) {
        this.clubRepository = clubRepository;
        this.federatedClubRepository = federatedClubRepository;
        this.eventBus = eventBus;
    }

    /**
     * Merges the selected clubs into the primary club and publishes the resulting domain events.
     *
     * <p>Each non-primary club's name is snapshotted <em>before</em> it is deleted, while the
     * primary club is renamed <em>after</em> the loop. The published
     * {@code ClubsConsolidatedEvent} therefore pairs pre-merge source names with the post-merge
     * canonical name, which is the intended audit semantics.</p>
     *
     * <p>Events are published only after the merge has been persisted, so no audit record can
     * describe a consolidation that failed to save.</p>
     */
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

        List<ConsolidationActionClub> mergedClubs = new ArrayList<>();
        for (UUID nonPrimaryClubId : nonPrimaryClubIds) {
            Club nonPrimaryClub = clubRepository.findClubById(nonPrimaryClubId).orElseThrow();
            mergedClubs.add(ConsolidationActionClub.of(nonPrimaryClub.getId(), nonPrimaryClub.getName()));
            reassignFederatedClubs(nonPrimaryClubId, primaryClub);
            clubRepository.deleteClubById(nonPrimaryClubId);
        }

        primaryClub.modifyName(canonicalName.trim());
        primaryClub.consolidate(mergedClubs, command.getPerformedByUserId(), command.getPerformedByUsername());
        clubRepository.saveClub(primaryClub);

        publishEvents(primaryClub);

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

    /**
     * Publishes every event queued on the aggregate, not only the consolidation event. The rename
     * performed above also queues a {@code ClubNameModifiedEvent}; draining the whole list keeps
     * aggregate-wide semantics and costs nothing while no subscriber handles it.
     */
    private void publishEvents(Club primaryClub) {
        primaryClub.getEvents().stream()
                .filter(DomainEvent.class::isInstance)
                .map(DomainEvent.class::cast)
                .forEach(eventBus::publish);
    }
}
