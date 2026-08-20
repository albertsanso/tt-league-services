package org.cttelsamicsterrassa.data.core.domain.club.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.club.event.TeamCreatedEvent;
import org.cttelsamicsterrassa.data.core.domain.club.event.TeamDeletedEvent;
import org.cttelsamicsterrassa.data.core.domain.club.event.TeamNameModifiedEvent;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public class Team extends Entity {
    private final UUID id;
    private final ImportSource source;
    private String name;
    private final Season season;
    private final Optional<Club> club;

    private Team(UUID id, ImportSource source, String name, Season season, Club club) {
        this.id = id;
        this.source = source;
        this.name = name;
        this.season = season;
        this.club = Optional.ofNullable(club);
    }

    private static Team of(UUID id, ImportSource source, String name, Season season, Club club) {
        return new Team(id, source, name, season, club);
    }

    public static Team createNew(ImportSource source, String name, Season season, Club club) {
        Team team = of(UUID.randomUUID(), source, name, season, club);
        team.publishTeamCreatedEvent();
        return team;
    }

    public static Team createExisting(UUID id, ImportSource source, String name, Season season, Club club) {
        return of(id, source, name, season, club);
    }

    /**
     * Returns an otherwise identical registration associated with {@code club}.
     * The original id, source, season-specific name, and season are retained.
     */
    public Team withClub(Club club) {
        if (club != null && this.club.isPresent() && club.getId().equals(this.club.get().getId())) {
            return this;
        }
        return of(id, source, name, season, club);
    }

    public void modifyName(String newName) {
        if (!this.name.equals(newName)) {
            this.name = newName;
            publishTeamNameModifiedEvent();
        }
    }

    public void delete() {
        publishTeamDeletedEvent();
    }

    private void publishTeamCreatedEvent() {
        publishEvent(TeamCreatedEvent.of(id, name, source, club.orElse(null)));
    }
    private void publishTeamNameModifiedEvent() {
        publishEvent(TeamNameModifiedEvent.of(id, name));
    }
    private void publishTeamDeletedEvent() {
        publishEvent(TeamDeletedEvent.of(id));
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

    public Optional<Club> getClub() {
        return club;
    }
}
