package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ClubNameModifiedEvent extends DomainEvent {

    private final UUID clubId;
    private final String clubName;

    private ClubNameModifiedEvent(UUID clubId, String clubName) {
        super(ZonedDateTime.now(), clubId.toString());
        this.clubId = clubId;
        this.clubName = clubName;
    }

    public static ClubNameModifiedEvent of(UUID clubId, String clubName) {
        return new ClubNameModifiedEvent(clubId, clubName);
    }

    public UUID getClubId() {
        return clubId;
    }

    public String getClubName() {
        return clubName;
    }
}
