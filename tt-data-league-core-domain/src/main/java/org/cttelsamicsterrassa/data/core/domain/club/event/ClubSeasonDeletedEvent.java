package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ClubSeasonDeletedEvent extends DomainEvent {
    private final UUID clubSeasonId;

    private ClubSeasonDeletedEvent(UUID clubSeasonId) {
        super(ZonedDateTime.now(), clubSeasonId.toString());
        this.clubSeasonId = clubSeasonId;
    }

    public static ClubSeasonDeletedEvent of(UUID clubSeasonId) {
        return new ClubSeasonDeletedEvent(clubSeasonId);
    }

    public UUID getClubSeasonId() {
        return clubSeasonId;
    }
}
