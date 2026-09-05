package org.cttelsamicsterrassa.data.core.application.importresource.process;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindImportRunStatusQuery extends DomainQuery {

    private final UUID runId;

    public FindImportRunStatusQuery(UUID runId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.runId = runId;
    }

    public UUID getRunId() {
        return runId;
    }
}
