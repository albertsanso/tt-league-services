package org.cttelsamicsterrassa.data.core.application.settings.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindSettingsByCategoryQuery extends DomainQuery {
    private final String category;

    public FindSettingsByCategoryQuery(String category) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.category = category;
    }

    public String getCategory() {
        return category;
    }
}
