package org.cttelsamicsterrassa.data.core.application.importresource.preview;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindImportPreviewStatusQuery extends DomainQuery {

    private final UUID importResourceId;

    public FindImportPreviewStatusQuery(UUID importResourceId) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.importResourceId = importResourceId;
    }

    public UUID getImportResourceId() {
        return importResourceId;
    }
}
