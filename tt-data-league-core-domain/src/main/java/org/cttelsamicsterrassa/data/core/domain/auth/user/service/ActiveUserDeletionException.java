package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

public class ActiveUserDeletionException extends RuntimeException {
    public ActiveUserDeletionException() {
        super("Cannot permanently delete an active user; deactivate first");
    }
}
