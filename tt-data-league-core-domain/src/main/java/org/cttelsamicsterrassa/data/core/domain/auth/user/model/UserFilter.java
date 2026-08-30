package org.cttelsamicsterrassa.data.core.domain.auth.user.model;

/**
 * Pagination and search criteria for administrator user queries.
 *
 * @param search  optional substring matched against username and email (null means no text filter)
 * @param active  optional active-status filter (null means all users)
 * @param page    0-based page number
 * @param size    page size (positive)
 */
public record UserFilter(String search, Boolean active, int page, int size) {

    public static final int DEFAULT_SIZE = 20;

    public UserFilter {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
    }

    public static UserFilter of(String search, Boolean active, int page, int size) {
        return new UserFilter(
                (search != null && search.isBlank()) ? null : search,
                active,
                page,
                size);
    }

    public static UserFilter defaults() {
        return new UserFilter(null, null, 0, DEFAULT_SIZE);
    }
}
