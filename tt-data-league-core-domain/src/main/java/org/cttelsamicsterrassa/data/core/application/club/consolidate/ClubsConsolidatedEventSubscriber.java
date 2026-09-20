package org.cttelsamicsterrassa.data.core.application.club.consolidate;

import org.albertsanso.commons.event.DomainEventSubscriber;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubsConsolidatedEvent;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationAction;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClub;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionType;
import org.cttelsamicsterrassa.data.core.domain.consolidation.repository.ConsolidationActionRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns a {@link ClubsConsolidatedEvent} into the persisted {@link ConsolidationAction} audit
 * record.
 */
@Named
public class ClubsConsolidatedEventSubscriber extends DomainEventSubscriber<ClubsConsolidatedEvent> {

    private static final Logger LOGGER = Logger.getLogger(ClubsConsolidatedEventSubscriber.class.getName());

    private final ConsolidationActionRepository consolidationActionRepository;

    @Inject
    public ClubsConsolidatedEventSubscriber(ConsolidationActionRepository consolidationActionRepository) {
        this.consolidationActionRepository = consolidationActionRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The merge is already persisted by the time this runs and the bus dispatches on the calling
     * thread, so a failure here must not propagate: losing the audit record is bad, failing a
     * consolidation that actually succeeded is worse.</p>
     */
    @Override
    public void handle(ClubsConsolidatedEvent event) {
        try {
            ConsolidationAction action = ConsolidationAction.createNew(
                    ConsolidationActionType.MERGE,
                    event.getOccurredOn(),
                    event.getPerformedByUserId(),
                    event.getPerformedByUsername(),
                    event.getCanonicalName(),
                    event.getMergedClubs(),
                    List.of(ConsolidationActionClub.of(event.getPrimaryClubId(), event.getPrimaryClubName())));
            consolidationActionRepository.save(action);
        } catch (RuntimeException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Failed to record consolidation action for club " + event.getPrimaryClubId(),
                    exception);
        }
    }
}
