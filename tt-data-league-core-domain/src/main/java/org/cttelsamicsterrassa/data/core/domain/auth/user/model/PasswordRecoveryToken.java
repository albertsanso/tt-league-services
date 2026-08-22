package org.cttelsamicsterrassa.data.core.domain.auth.user.model;

import java.time.ZonedDateTime;
import java.util.UUID;

public record PasswordRecoveryToken(
        UUID id,
        UUID userId,
        String tokenHash,
        ZonedDateTime createdAt,
        ZonedDateTime expiresAt,
        boolean consumed) {

    public static PasswordRecoveryToken createNew(
            UUID userId,
            String tokenHash,
            ZonedDateTime createdAt,
            ZonedDateTime expiresAt) {
        return new PasswordRecoveryToken(
                UUID.randomUUID(), userId, tokenHash, createdAt, expiresAt, false);
    }

    public boolean isActiveAt(ZonedDateTime now) {
        return !consumed && expiresAt.isAfter(now);
    }
}
