package org.cttelsamicsterrassa.data.core.application.settings.find;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.settings.model.SettingCategory;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindSettingsByCategoryQuery extends DomainQuery {
    private final SettingCategory category;

    public FindSettingsByCategoryQuery(SettingCategory category) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.category = category;
    }

    public SettingCategory getCategory() {
        return category;
    }
}
