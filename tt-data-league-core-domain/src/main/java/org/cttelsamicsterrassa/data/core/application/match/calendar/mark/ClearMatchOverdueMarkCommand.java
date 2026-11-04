package org.cttelsamicsterrassa.data.core.application.match.calendar.mark;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Clears the manual overdue mark of a match (FEAT-00092). Idempotent: clearing a match with no
 * mark, or a PLAYED match with a leftover mark, succeeds.
 */
public class ClearMatchOverdueMarkCommand extends DomainCommand {

    private final UUID matchId;

    public ClearMatchOverdueMarkCommand(UUID matchId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.matchId = matchId;
    }

    public UUID getMatchId() {
        return matchId;
    }
}