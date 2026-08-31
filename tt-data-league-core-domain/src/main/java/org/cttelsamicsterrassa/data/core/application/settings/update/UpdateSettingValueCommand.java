package org.cttelsamicsterrassa.data.core.application.settings.update;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class UpdateSettingValueCommand extends DomainCommand {
    private final UUID settingId;
    private final String value;

    public UpdateSettingValueCommand(UUID settingId, String value) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.settingId = settingId;
        this.value = value;
    }

    public UUID getSettingId() {
        return settingId;
    }

    public String getValue() {
        return value;
    }
}
