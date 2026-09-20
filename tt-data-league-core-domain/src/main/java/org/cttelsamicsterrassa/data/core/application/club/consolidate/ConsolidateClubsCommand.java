package org.cttelsamicsterrassa.data.core.application.club.consolidate;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class ConsolidateClubsCommand extends DomainCommand {
    private final List<UUID> clubIds;
    private final String canonicalName;
    private final UUID primaryClubId;
    private final UUID performedByUserId;
    private final String performedByUsername;

    public ConsolidateClubsCommand(
            ZonedDateTime occurredOn,
            String uuid,
            List<UUID> clubIds,
            String canonicalName,
            UUID primaryClubId,
            UUID performedByUserId,
            String performedByUsername) {
        super(occurredOn, uuid);
        this.clubIds = clubIds == null ? List.of() : List.copyOf(clubIds);
        this.canonicalName = canonicalName;
        this.primaryClubId = primaryClubId;
        this.performedByUserId = performedByUserId;
        this.performedByUsername = performedByUsername;
    }

    public List<UUID> getClubIds() {
        return clubIds;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public UUID getPrimaryClubId() {
        return primaryClubId;
    }

    /**
     * The authenticated user that requested the consolidation, or {@code null} when the command was
     * not raised by an authenticated caller. Never taken from the request body.
     */
    public UUID getPerformedByUserId() {
        return performedByUserId;
    }

    public String getPerformedByUsername() {
        return performedByUsername;
    }
}
