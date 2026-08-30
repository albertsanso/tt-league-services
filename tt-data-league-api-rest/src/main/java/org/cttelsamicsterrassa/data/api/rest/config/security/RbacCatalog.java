package org.cttelsamicsterrassa.data.api.rest.config.security;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.Permission;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class RbacCatalog {
    public static final String ADMIN = "ADMIN";
    public static final String CLUB_MANAGER = "CLUB_MANAGER";
    public static final String ANALYST = "ANALYST";
    public static final String PRACTITIONER = "PRACTITIONER";

    public static final String CLUBS_READ = "clubs:read";
    public static final String CLUBS_WRITE = "clubs:write";
    public static final String PLAYERS_READ = "players:read";
    public static final String MATCHES_READ = "matches:read";
    public static final String USERS_READ = "users:read";
    public static final String USERS_WRITE = "users:write";

    private RbacCatalog() {
    }

    public static Set<String> roleNames(User user) {
        return user.getRoles().stream().map(UserRole::name).collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> permissionNames(User user) {
        return user.getPermissions().stream().map(Permission::value).collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> permissionNames(Collection<String> roleNames) {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        roleNames.stream()
                .map(RbacCatalog::role)
                .forEach(role -> permissions.addAll(role.permissions()));
        return permissions.stream().map(Permission::value).collect(Collectors.toUnmodifiableSet());
    }

    public static UserRole role(String roleName) {
        return UserRole.valueOf(roleName);
    }
}
