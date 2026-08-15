package org.cttelsamicsterrassa.data.core.application.club.create;

import org.albertsanso.commons.command.DomainCommand;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.UUID;

public class CreateClubCommand extends DomainCommand {
    private final UUID clubId;
    private final String clubName;
    private final ImportSource source;

    protected CreateClubCommand(ZonedDateTime occurredOn, String uuid, UUID clubId, String clubName, ImportSource source) {
        super(occurredOn, uuid);
        this.clubId = clubId;
        this.clubName = clubName;
        this.source = source;
    }

    public UUID getClubId() {
        return clubId;
    }

    public String getClubName() {
        return clubName;
    }

    public ImportSource getSource() {
        return source;
    }
}
