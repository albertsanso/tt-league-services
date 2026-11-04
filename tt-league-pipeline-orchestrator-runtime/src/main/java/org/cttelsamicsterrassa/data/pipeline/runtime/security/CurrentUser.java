package org.cttelsamicsterrassa.data.pipeline.runtime.security;

import org.springframework.security.core.Authentication;

/** The authenticated platform user, taken from the JWT subject. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Name for {@code requestedBy}; fails when the request is not authenticated. */
    public static String name(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new IllegalStateException("No authenticated user");
        }
        return authentication.getName();
    }
}
