package org.cttelsamicsterrassa.data.core.application.settings.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindSettingByIdQuery extends DomainQuery {
    private final UUID settingId;

    public FindSettingByIdQuery(UUID settingId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.settingId = settingId;
    }

    public UUID getSettingId() {
        return settingId;
    }
}
