package org.cttelsamicsterrassa.data.core.repository.jpa.auth.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.PasswordRecoveryToken;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.PasswordRecoveryTokenRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.auth.mapper.PasswordRecoveryTokenJPAToTokenMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.auth.mapper.PasswordRecoveryTokenToJPAMapper;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@RequiredArgsConstructor
public class PasswordRecoveryTokenRepositoryJpa implements PasswordRecoveryTokenRepository {
    private final PasswordRecoveryTokenRepositoryHelper helper;
    private final PasswordRecoveryTokenJPAToTokenMapper toDomain;
    private final PasswordRecoveryTokenToJPAMapper toJpa;

    @Override
    public void save(PasswordRecoveryToken token) {
        helper.save(toJpa.apply(token));
    }

    @Override
    public Optional<PasswordRecoveryToken> findActiveByTokenHash(String tokenHash, ZonedDateTime now) {
        return helper.findByTokenHashAndConsumedFalseAndExpiresAtAfter(tokenHash, now).map(toDomain);
    }

    @Override
    public boolean consumeIfActive(UUID tokenId, ZonedDateTime now) {
        return helper.consumeIfActive(tokenId, now) == 1;
    }

    @Override
    public void consumeAllForUser(UUID userId, ZonedDateTime now) {
        helper.consumeAllForUser(userId, now);
    }
}
