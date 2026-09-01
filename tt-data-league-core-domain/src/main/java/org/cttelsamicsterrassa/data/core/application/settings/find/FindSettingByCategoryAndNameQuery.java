package org.cttelsamicsterrassa.data.core.application.settings.find;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindSettingByCategoryAndNameQuery extends DomainQuery {
    private final SettingCategory category;
    private final String name;

    public FindSettingByCategoryAndNameQuery(SettingCategory category, String name) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.category = category;
        this.name = name;
    }

    public SettingCategory getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }
}
