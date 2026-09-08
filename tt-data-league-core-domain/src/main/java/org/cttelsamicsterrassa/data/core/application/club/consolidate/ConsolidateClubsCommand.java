package org.cttelsamicsterrassa.data.core.application.club.consolidate;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class ConsolidateClubsCommand extends DomainCommand {
    private final List<UUID> clubIds;
    private final String canonicalName;
    private final UUID primaryClubId;

    public ConsolidateClubsCommand(
            ZonedDateTime occurredOn, String uuid, List<UUID> clubIds, String canonicalName, UUID primaryClubId) {
        super(occurredOn, uuid);
        this.clubIds = clubIds == null ? List.of() : List.copyOf(clubIds);
        this.canonicalName = canonicalName;
        this.primaryClubId = primaryClubId;
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
}
