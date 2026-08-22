package org.cttelsamicsterrassa.data.core.application.auth.user.find;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class FindUserByNameQuery extends DomainQuery {
    private final String username;

    public FindUserByNameQuery(String username) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
