package org.cttelsamicsterrassa.data.core.application.settings.create;

import org.albertsanso.commons.command.DomainCommand;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;

import java.time.ZonedDateTime;
import java.util.UUID;

public class CreateSettingCommand extends DomainCommand {
    private final SettingCategory category;
    private final String name;
    private final String value;

    public CreateSettingCommand(SettingCategory category, String name, String value) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.category = category;
        this.name = name;
        this.value = value;
    }

    public SettingCategory getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
