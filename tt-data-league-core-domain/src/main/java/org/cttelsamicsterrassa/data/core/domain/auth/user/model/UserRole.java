package org.cttelsamicsterrassa.data.core.domain.auth.user.model;

import java.util.Set;

public enum UserRole {
    ADMIN(Set.of(Permission.USERS_READ, Permission.USERS_WRITE, Permission.CLUBS_READ,
            Permission.CLUBS_WRITE, Permission.PLAYERS_READ, Permission.MATCHES_READ,
            Permission.ANALYTICS_READ)),
    CLUB_MANAGER(Set.of(Permission.CLUBS_READ, Permission.CLUBS_WRITE,
            Permission.PLAYERS_READ, Permission.MATCHES_READ)),
    ANALYST(Set.of(Permission.CLUBS_READ, Permission.PLAYERS_READ,
            Permission.MATCHES_READ, Permission.ANALYTICS_READ)),
    PRACTITIONER(Set.of(Permission.CLUBS_READ, Permission.PLAYERS_READ,
            Permission.MATCHES_READ));

    private final Set<Permission> permissions;

    UserRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
