package org.cttelsamicsterrassa.data.core.application.settings.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindSettingByCategoryAndNameQuery extends DomainQuery {
    private final String category;
    private final String name;

    public FindSettingByCategoryAndNameQuery(String category, String name) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.category = category;
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }
}
