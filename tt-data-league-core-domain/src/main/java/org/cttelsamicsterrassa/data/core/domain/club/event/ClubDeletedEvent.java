package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ClubDeletedEvent extends DomainEvent {

    private final UUID clubId;
    private final String clubName;

    private ClubDeletedEvent(UUID clubId, String clubName) {
        super(ZonedDateTime.now(), clubId.toString());
        this.clubId = clubId;
        this.clubName = clubName;
    }

    public static ClubDeletedEvent of(UUID clubId, String clubName) {
        return new ClubDeletedEvent(clubId, clubName);
    }

    public UUID getClubId() {
        return clubId;
    }

    public String getClubName() {
        return clubName;
    }
}
