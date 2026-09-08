package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class ClubsConsolidatedEvent extends DomainEvent {
    private final UUID primaryClubId;
    private final String canonicalName;
    private final List<UUID> mergedClubIds;

    private ClubsConsolidatedEvent(UUID primaryClubId, String canonicalName, List<UUID> mergedClubIds) {
        super(ZonedDateTime.now(), primaryClubId.toString());
        this.primaryClubId = primaryClubId;
        this.canonicalName = canonicalName;
        this.mergedClubIds = mergedClubIds;
    }

    public static ClubsConsolidatedEvent of(UUID primaryClubId, String canonicalName, List<UUID> mergedClubIds) {
        return new ClubsConsolidatedEvent(primaryClubId, canonicalName, List.copyOf(mergedClubIds));
    }

    public UUID getPrimaryClubId() {
        return primaryClubId;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public List<UUID> getMergedClubIds() {
        return mergedClubIds;
    }
}
