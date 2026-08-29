package org.cttelsamicsterrassa.data.core.application.player.find;

import org.albertsanso.commons.query.DomainQuery;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindFederatedPlayersByStringInNameQuery extends DomainQuery {
    private final String stringToSearch;
    private final ImportSource source;

    public FindFederatedPlayersByStringInNameQuery(String stringToSearch) {
        this(stringToSearch, null);
    }

    public FindFederatedPlayersByStringInNameQuery(String stringToSearch, ImportSource source) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.stringToSearch = stringToSearch;
        this.source = source;
    }

    public String getStringToSearch() {
        return stringToSearch;
    }

    public ImportSource getSource() {
        return source;
    }
}
