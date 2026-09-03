package org.cttelsamicsterrassa.data.core.application.importresource.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindImportResourcesBySourceQuery extends DomainQuery {
    private final String source;

    public FindImportResourcesBySourceQuery(String source) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}
