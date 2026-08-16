package org.cttelsamicsterrassa.data.core.domain.lineup.event;

import org.albertsanso.commons.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.UUID;

public class LineupDeletedEvent extends DomainEvent {

    private final UUID lineupId;

    private LineupDeletedEvent(UUID lineupId) {
        super(ZonedDateTime.now(), lineupId.toString());
        this.lineupId = lineupId;
    }

    public static LineupDeletedEvent of(UUID lineupId) {
        return new LineupDeletedEvent(lineupId);
    }

    public UUID getLineupId() {
        return lineupId;
    }
}
