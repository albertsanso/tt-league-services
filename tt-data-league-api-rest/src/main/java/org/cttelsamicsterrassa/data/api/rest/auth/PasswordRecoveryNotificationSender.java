package org.cttelsamicsterrassa.data.api.rest.auth;

import org.cttelsamicsterrassa.data.core.domain.auth.user.service.PasswordRecoveryService.RecoveryNotification;

@FunctionalInterface
public interface PasswordRecoveryNotificationSender {
    void send(RecoveryNotification notification);
}
