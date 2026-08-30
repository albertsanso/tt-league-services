package org.cttelsamicsterrassa.data.core.domain.auth.user.model;

import java.util.List;

/**
 * Paginated result of a user query.
 *
 * @param content       users on this page
 * @param totalElements total matching users across all pages
 * @param totalPages    total number of pages
 * @param page          0-based current page
 * @param size          requested page size
 */
public record UserPage(List<User> content, long totalElements, int totalPages, int page, int size) {

    public static UserPage of(List<User> content, long totalElements, int page, int size) {
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        return new UserPage(List.copyOf(content), totalElements, totalPages, page, size);
    }
}
