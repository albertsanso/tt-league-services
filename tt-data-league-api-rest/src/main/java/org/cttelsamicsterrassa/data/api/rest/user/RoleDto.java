package org.cttelsamicsterrassa.data.api.rest.user;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;

import java.util.Arrays;
import java.util.List;

public record RoleDto(String name, List<String> permissions) {

    public static RoleDto fromRole(UserRole role) {
        List<String> perms = role.permissions().stream().map(p -> p.value()).sorted().toList();
        return new RoleDto(role.name(), perms);
    }

    public static List<RoleDto> catalog() {
        return Arrays.stream(UserRole.values()).map(RoleDto::fromRole).toList();
    }
}
