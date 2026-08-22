package org.cttelsamicsterrassa.data.core.domain.auth.user.model;

public enum Permission {
    USERS_READ("users:read"),
    USERS_WRITE("users:write"),
    CLUBS_READ("clubs:read"),
    CLUBS_WRITE("clubs:write"),
    PLAYERS_READ("players:read"),
    MATCHES_READ("matches:read"),
    ANALYTICS_READ("analytics:read");

    private final String value;

    Permission(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
