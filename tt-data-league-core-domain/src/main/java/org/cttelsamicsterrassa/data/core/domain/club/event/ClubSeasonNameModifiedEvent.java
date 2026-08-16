package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ClubSeasonNameModifiedEvent extends DomainEvent {
    private final UUID clubSeasonId;
    private final String newClubSeasonName;

    private ClubSeasonNameModifiedEvent(UUID clubSeasonId, String newClubSeasonName) {
        super(ZonedDateTime.now(), clubSeasonId.toString());
        this.clubSeasonId = clubSeasonId;
        this.newClubSeasonName = newClubSeasonName;
    }

    public static ClubSeasonNameModifiedEvent of(UUID clubSeasonId, String newClubSeasonName) {
        return new ClubSeasonNameModifiedEvent(clubSeasonId, newClubSeasonName);
    }

    public UUID getClubSeasonId() {
        return clubSeasonId;
    }

    public String getNewClubSeasonName() {
        return newClubSeasonName;
    }
}
