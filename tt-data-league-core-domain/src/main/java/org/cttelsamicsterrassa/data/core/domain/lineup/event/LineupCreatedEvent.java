package org.cttelsamicsterrassa.data.core.domain.lineup.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class LineupCreatedEvent extends DomainEvent {

    private final UUID lineupId;

    private LineupCreatedEvent(UUID lineupId) {
        super(ZonedDateTime.now(), lineupId.toString());
        this.lineupId = lineupId;
    }

    public static LineupCreatedEvent of(UUID lineupId) {
        return new LineupCreatedEvent(lineupId);
    }

    public UUID getLineupId() {
        return lineupId;
    }
}
