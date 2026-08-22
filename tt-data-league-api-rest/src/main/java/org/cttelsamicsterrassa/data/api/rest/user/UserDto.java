package org.cttelsamicsterrassa.data.api.rest.user;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;

import java.time.ZonedDateTime;
import java.util.UUID;

public record UserDto(UUID id, String username, String email, ZonedDateTime createdAt, boolean active) {
    public static UserDto fromObject(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt(),
                user.isActived()
        );
    }
}
