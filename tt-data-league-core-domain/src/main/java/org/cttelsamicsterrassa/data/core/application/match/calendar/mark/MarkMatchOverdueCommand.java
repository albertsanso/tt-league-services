package org.cttelsamicsterrassa.data.core.application.match.calendar.mark;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Marks a SCHEDULED match as overdue manually (FEAT-00092). {@code markedBy} is the authenticated
 * user name and is never taken from the request body.
 */
public class MarkMatchOverdueCommand extends DomainCommand {

    private final UUID matchId;
    private final String markedBy;

    public MarkMatchOverdueCommand(UUID matchId, String markedBy) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.matchId = matchId;
        this.markedBy = markedBy;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public String getMarkedBy() {
        return markedBy;
    }
}