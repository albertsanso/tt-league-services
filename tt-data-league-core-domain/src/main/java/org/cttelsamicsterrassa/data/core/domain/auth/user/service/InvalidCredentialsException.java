package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}

