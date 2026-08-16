package org.cttelsamicsterrassa.data.core.domain.club.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubSeasonCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubSeasonDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubSeasonNameModifiedEvent;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ClubSeason extends Entity {
    private final UUID id;
    private final ImportSource source;
    private String name;
    private final Season season;
    private final Club club;

    private ClubSeason(UUID id, ImportSource source, String name, Season season, Club club) {
        this.id = id;
        this.source = source;
        this.name = name;
        this.season = season;
        this.club = club;
    }

    private static ClubSeason of(UUID id, ImportSource source, String name, Season season, Club club) {
        return new ClubSeason(id, source, name, season, club);
    }

    public static ClubSeason createNew(ImportSource source, String name, Season season, Club club) {
        ClubSeason clubSeason = of(UUID.randomUUID(), source, name, season, club);
        clubSeason.publishClubSeasonCreatedEvent();
        return clubSeason;
    }

    public static ClubSeason createExisting(UUID id, ImportSource source, String name, Season season, Club club) {
        return of(id, source, name, season, club);
    }

    public void modifyName(String newName) {
        if (!this.name.equals(newName)) {
            this.name = newName;
            publishClubSeasonNameModifiedEvent();
        }
    }

    public void delete() {
        publishClubSeasonDeletedEvent();
    }

    private void publishClubSeasonCreatedEvent() {
        publishEvent(ClubSeasonCreatedEvent.of(id, name, source, club));
    }
    private void publishClubSeasonNameModifiedEvent() {
        publishEvent(ClubSeasonNameModifiedEvent.of(id, name));
    }
    private void publishClubSeasonDeletedEvent() {
        publishEvent(ClubSeasonDeletedEvent.of(id));
    }

    public UUID getId() {
        return id;
    }

    public ImportSource getSource() {
        return source;
    }

    public String getName() {
        return name;
    }

    public Season getSeason() {
        return season;
    }

    public Club getClub() {
        return club;
    }
}
