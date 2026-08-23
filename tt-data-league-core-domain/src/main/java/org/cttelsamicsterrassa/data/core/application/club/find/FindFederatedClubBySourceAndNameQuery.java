package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

public class FindFederatedClubBySourceAndNameQuery extends DomainQuery {

    private final ImportSource source;
    private final String name;

    public FindFederatedClubBySourceAndNameQuery(ImportSource source, String name) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.name = name;
    }

    public ImportSource getSource() {
        return source;
    }

    public String getName() {
        return name;
    }
}
