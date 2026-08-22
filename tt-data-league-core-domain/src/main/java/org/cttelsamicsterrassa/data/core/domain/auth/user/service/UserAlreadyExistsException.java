package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}

