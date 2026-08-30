package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

public class SelfDeactivationException extends RuntimeException {
    public SelfDeactivationException() {
        super("Administrators cannot deactivate their own account");
    }
}
