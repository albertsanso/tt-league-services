package org.cttelsamicsterrassa.data.core.domain.auth.user.repository;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.PasswordRecoveryToken;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordRecoveryTokenRepository {
    void save(PasswordRecoveryToken token);

    Optional<PasswordRecoveryToken> findActiveByTokenHash(String tokenHash, ZonedDateTime now);

    boolean consumeIfActive(UUID tokenId, ZonedDateTime now);

    void consumeAllForUser(UUID userId, ZonedDateTime now);
}
