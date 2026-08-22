package org.cttelsamicsterrassa.data.core.application.auth.user.login;

import org.albertsanso.commons.query.DomainQuery;

import java.time.ZonedDateTime;
import java.util.UUID;

public class AuthenticateUserQuery extends DomainQuery {
    private final String username;
    private final String password;

    public AuthenticateUserQuery(String username, String password) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
