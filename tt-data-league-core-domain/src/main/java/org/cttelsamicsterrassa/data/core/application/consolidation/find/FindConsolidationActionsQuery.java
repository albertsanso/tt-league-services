package org.cttelsamicsterrassa.data.core.application.consolidation.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Finds consolidation history, either in full or restricted to one club id.
 */
public class FindConsolidationActionsQuery extends DomainQuery {
    private final UUID clubId;

    public FindConsolidationActionsQuery() {
        this(null);
    }

    public FindConsolidationActionsQuery(UUID clubId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.clubId = clubId;
    }

    /**
     * The club to restrict the history to, or {@code null} for the full log. The club is matched on
     * either side of an action, so ids that no longer resolve still return their history.
     */
    public UUID getClubId() {
        return clubId;
    }
}
