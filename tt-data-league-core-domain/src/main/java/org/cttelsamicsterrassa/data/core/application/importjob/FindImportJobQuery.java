package org.cttelsamicsterrassa.data.core.application.importjob;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

public class FindImportJobQuery extends DomainQuery {
    private final UUID importJobId;

    public FindImportJobQuery(UUID importJobId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.importJobId = Objects.requireNonNull(importJobId, "importJobId");
    }

    public UUID getImportJobId() {
        return importJobId;
    }
}
