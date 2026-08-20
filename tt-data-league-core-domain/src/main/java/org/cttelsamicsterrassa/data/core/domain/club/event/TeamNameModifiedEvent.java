package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class TeamNameModifiedEvent extends DomainEvent {
    private final UUID teamId;
    private final String newTeamName;

    private TeamNameModifiedEvent(UUID teamId, String newTeamName) {
        super(ZonedDateTime.now(), teamId.toString());
        this.teamId = teamId;
        this.newTeamName = newTeamName;
    }

    public static TeamNameModifiedEvent of(UUID teamId, String newTeamName) {
        return new TeamNameModifiedEvent(teamId, newTeamName);
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getNewTeamName() {
        return newTeamName;
    }
}
