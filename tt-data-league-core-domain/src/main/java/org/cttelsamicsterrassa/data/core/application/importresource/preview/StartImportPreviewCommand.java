package org.cttelsamicsterrassa.data.core.application.importresource.preview;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class StartImportPreviewCommand extends DomainCommand {
    private final UUID importResourceId;


    public StartImportPreviewCommand(UUID importResourceId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.importResourceId = importResourceId;
    }

    public UUID getImportResourceId() {
        return importResourceId;
    }
}
