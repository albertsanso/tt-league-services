package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindClubBySourceAndNameQuery extends DomainQuery {

    private final ImportSource source;
    private final String name;

    public FindClubBySourceAndNameQuery(ImportSource source, String name) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.source = source;
        this.name = name;
    }

    public ImportSource getSource() {
        return source;
    }

    public String getName() {
        return name;
    }
}
