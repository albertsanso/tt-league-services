package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public class TeamCreatedEvent extends DomainEvent {
    private final UUID teamId;
    private final String teamName;
    private final ImportSource source;
    private final Optional<Club> club;

    private TeamCreatedEvent(UUID teamId, String teamName, ImportSource source, Club club) {
        super(ZonedDateTime.now(), teamId.toString());
        this.teamId = teamId;
        this.teamName = teamName;
        this.source = source;
        this.club = Optional.ofNullable(club);
    }

    public static TeamCreatedEvent of(UUID teamId, String teamName, ImportSource source, Club club) {
        return new TeamCreatedEvent(teamId, teamName, source, club);
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public Optional<Club> getClub() {
        return club;
    }

    public ImportSource getSource() {
        return source;
    }
}
