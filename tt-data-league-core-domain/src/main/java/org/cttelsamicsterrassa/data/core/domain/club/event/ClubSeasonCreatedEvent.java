package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ClubSeasonCreatedEvent extends DomainEvent {
    private final UUID clubSeasonId;
    private final String clubSeasonName;
    private final ImportSource source;
    private final Club club;

    private ClubSeasonCreatedEvent(UUID clubSeasonId, String clubSeasonName, ImportSource source, Club club) {
        super(ZonedDateTime.now(), clubSeasonId.toString());
        this.clubSeasonId = clubSeasonId;
        this.clubSeasonName = clubSeasonName;
        this.source = source;
        this.club = club;
    }

    public static ClubSeasonCreatedEvent of(UUID clubSeasonId, String clubSeasonName, ImportSource source, Club club) {
        return new ClubSeasonCreatedEvent(clubSeasonId, clubSeasonName, source, club);
    }

    public UUID getClubSeasonId() {
        return clubSeasonId;
    }

    public String getClubSeasonName() {
        return clubSeasonName;
    }

    public Club getClub() {
        return club;
    }

    public ImportSource getSource() {
        return source;
    }
}
