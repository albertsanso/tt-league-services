package org.cttelsamicsterrassa.data.core.domain.club.model;

import org.albertsanso.commons.model.Entity;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.UUID;

public class ClubSeason extends Entity {
    private final UUID id;
    private final ImportSource source;
    private final String name;
    private final Season season;
    private final Club club;

    private ClubSeason(UUID id, ImportSource source, String name, Season season, Club club) {
        this.id = id;
        this.source = source;
        this.name = name;
        this.season = season;
        this.club = club;
    }

    public static ClubSeason of(UUID id, ImportSource source, String name, Season season, Club club) {
        return new ClubSeason(id, source, name, season, club);
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
