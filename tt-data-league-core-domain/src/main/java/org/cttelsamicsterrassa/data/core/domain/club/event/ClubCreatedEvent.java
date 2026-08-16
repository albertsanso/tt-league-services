package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ClubCreatedEvent extends DomainEvent {

    private final UUID clubId;
    private final String clubName;

    private ClubCreatedEvent(UUID clubId, String clubName) {
        super(ZonedDateTime.now(), clubId.toString());
        this.clubName = clubName;
        this.clubId = clubId;
    }

    public static ClubCreatedEvent of(UUID clubId, String clubName) {
        return new ClubCreatedEvent(clubId, clubName);
    }

    public UUID getClubId() {
        return clubId;
    }

    public String getClubName() {
        return clubName;
    }
}
