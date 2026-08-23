package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ClubNameModifiedEvent extends DomainEvent {
    private final UUID clubId;
    private final String newClubName;

    private ClubNameModifiedEvent(UUID clubId, String newClubName) {
        super(ZonedDateTime.now(), clubId.toString());
        this.clubId = clubId;
        this.newClubName = newClubName;
    }

    public static ClubNameModifiedEvent of(UUID clubId, String newClubName) {
        return new ClubNameModifiedEvent(clubId, newClubName);
    }

    public UUID getClubId() {
        return clubId;
    }

    public String getNewClubName() {
        return newClubName;
    }
}
