package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindClubsByStringInNameQuery extends DomainQuery {

    private final String stringToSearch;

    public FindClubsByStringInNameQuery(String stringToSearch) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.stringToSearch = stringToSearch;
    }

    public String getStringToSearch() {
        return stringToSearch;
    }
}
