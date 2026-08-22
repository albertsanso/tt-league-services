package org.cttelsamicsterrassa.data.core.domain.auth.service;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}

