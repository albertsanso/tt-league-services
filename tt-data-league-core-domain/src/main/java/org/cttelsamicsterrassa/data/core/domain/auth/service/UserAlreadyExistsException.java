package org.cttelsamicsterrassa.data.core.domain.auth.service;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}

