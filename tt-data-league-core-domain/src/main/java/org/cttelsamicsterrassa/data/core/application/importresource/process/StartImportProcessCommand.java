package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class StartImportProcessCommand extends DomainCommand {
    private final UUID importResourceId;

    public StartImportProcessCommand(UUID importResourceId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.importResourceId = importResourceId;
    }

    public UUID getImportResourceId() {
        return importResourceId;
    }
}
