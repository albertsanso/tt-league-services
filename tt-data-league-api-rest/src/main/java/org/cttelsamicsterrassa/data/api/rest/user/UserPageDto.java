package org.cttelsamicsterrassa.data.api.rest.user;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserPage;

import java.util.List;

public record UserPageDto(
        List<UserDto> content,
        long totalElements,
        int totalPages,
        int page,
        int size) {

    public static UserPageDto fromDomain(UserPage page) {
        List<UserDto> content = page.content().stream().map(UserDto::fromObject).toList();
        return new UserPageDto(content, page.totalElements(), page.totalPages(), page.page(), page.size());
    }
}
