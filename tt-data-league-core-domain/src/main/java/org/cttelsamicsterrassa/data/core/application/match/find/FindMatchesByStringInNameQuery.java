package org.cttelsamicsterrassa.data.core.application.match.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindMatchesByStringInNameQuery extends DomainQuery {
    private final String stringToSearch;

    public FindMatchesByStringInNameQuery(String stringToSearch) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.stringToSearch = stringToSearch;
    }

    public String getStringToSearch() {
        return stringToSearch;
    }
}
