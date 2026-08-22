package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

public class InvalidRecoveryTokenException extends RuntimeException {
    public InvalidRecoveryTokenException() {
        super("Recovery token is invalid or expired");
    }
}
