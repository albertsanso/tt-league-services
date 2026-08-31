package org.cttelsamicsterrassa.data.core.application.settings.delete;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class DeleteSettingCommand extends DomainCommand {
    private final UUID settingId;

    public DeleteSettingCommand(UUID settingId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.settingId = settingId;
    }

    public UUID getSettingId() {
        return settingId;
    }
}
