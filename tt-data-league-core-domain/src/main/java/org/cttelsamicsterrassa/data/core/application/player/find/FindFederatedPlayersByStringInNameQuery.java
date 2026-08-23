package org.cttelsamicsterrassa.data.core.application.player.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindFederatedPlayersByStringInNameQuery extends DomainQuery {
    private final String stringToSearch;

    public FindFederatedPlayersByStringInNameQuery(String stringToSearch) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.stringToSearch = stringToSearch;
    }

    public String getStringToSearch() {
        return stringToSearch;
    }
}
