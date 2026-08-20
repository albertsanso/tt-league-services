package org.cttelsamicsterrassa.data.core.domain.club.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class TeamDeletedEvent extends DomainEvent {
    private final UUID teamId;

    private TeamDeletedEvent(UUID teamId) {
        super(ZonedDateTime.now(), teamId.toString());
        this.teamId = teamId;
    }

    public static TeamDeletedEvent of(UUID teamId) {
        return new TeamDeletedEvent(teamId);
    }

    public UUID getTeamId() {
        return teamId;
    }
}
