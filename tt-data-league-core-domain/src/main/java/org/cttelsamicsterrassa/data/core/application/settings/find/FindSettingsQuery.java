package org.cttelsamicsterrassa.data.core.application.settings.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindSettingsQuery extends DomainQuery {
    public FindSettingsQuery() {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
    }
}
