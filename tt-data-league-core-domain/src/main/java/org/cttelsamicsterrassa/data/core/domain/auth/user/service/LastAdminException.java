package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("Cannot deactivate the last administrator account");
    }
}
