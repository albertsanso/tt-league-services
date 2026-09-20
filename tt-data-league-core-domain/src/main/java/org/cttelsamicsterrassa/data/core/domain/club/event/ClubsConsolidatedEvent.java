package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClub;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Published when several clubs are merged into one primary club.
 *
 * <p>The event carries the merged clubs' names as well as their ids: by the time a subscriber runs,
 * those club rows have been deleted and the names cannot be looked up any more.</p>
 */
public class ClubsConsolidatedEvent extends DomainEvent {
    private final UUID primaryClubId;
    private final String primaryClubName;
    private final String canonicalName;
    private final List<ConsolidationActionClub> mergedClubs;
    private final UUID performedByUserId;
    private final String performedByUsername;

    private ClubsConsolidatedEvent(
            UUID primaryClubId,
            String primaryClubName,
            String canonicalName,
            List<ConsolidationActionClub> mergedClubs,
            UUID performedByUserId,
            String performedByUsername) {
        super(ZonedDateTime.now(), primaryClubId.toString());
        this.primaryClubId = primaryClubId;
        this.primaryClubName = primaryClubName;
        this.canonicalName = canonicalName;
        this.mergedClubs = mergedClubs;
        this.performedByUserId = performedByUserId;
        this.performedByUsername = performedByUsername;
    }

    public static ClubsConsolidatedEvent of(
            UUID primaryClubId,
            String primaryClubName,
            String canonicalName,
            List<ConsolidationActionClub> mergedClubs,
            UUID performedByUserId,
            String performedByUsername) {
        return new ClubsConsolidatedEvent(
                primaryClubId,
                primaryClubName,
                canonicalName,
                mergedClubs == null ? List.of() : List.copyOf(mergedClubs),
                performedByUserId,
                performedByUsername);
    }

    public UUID getPrimaryClubId() {
        return primaryClubId;
    }

    public String getPrimaryClubName() {
        return primaryClubName;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public List<ConsolidationActionClub> getMergedClubs() {
        return mergedClubs;
    }

    public List<UUID> getMergedClubIds() {
        return mergedClubs.stream().map(ConsolidationActionClub::getClubId).toList();
    }

    public UUID getPerformedByUserId() {
        return performedByUserId;
    }

    public String getPerformedByUsername() {
        return performedByUsername;
    }
}
