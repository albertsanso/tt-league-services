package org.cttelsamicsterrassa.data.api.rest.user;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.List;

public record UserDto(
        UUID id,
        String username,
        String email,
        ZonedDateTime createdAt,
        boolean active,
        List<String> roles,
        List<String> permissions) {
    public UserDto(UUID id, String username, String email, ZonedDateTime createdAt, boolean active) {
        this(id, username, email, createdAt, active,
                List.of("PRACTITIONER"),
                List.of("clubs:read", "matches:read", "players:read"));
    }

    public static UserDto fromObject(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt(),
                user.isActived(),
                user.getRoles().stream().map(Enum::name).sorted().toList(),
                user.getPermissions().stream().map(permission -> permission.value()).sorted().toList()
        );
    }
}
