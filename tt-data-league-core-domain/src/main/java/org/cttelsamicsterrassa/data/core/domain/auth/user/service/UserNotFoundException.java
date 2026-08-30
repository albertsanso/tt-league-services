package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }
}
