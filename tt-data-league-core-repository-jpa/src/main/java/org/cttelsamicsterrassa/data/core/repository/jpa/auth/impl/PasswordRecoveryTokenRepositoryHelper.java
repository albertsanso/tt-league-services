package org.cttelsamicsterrassa.data.core.repository.jpa.auth.impl;

import jakarta.transaction.Transactional;
import org.cttelsamicsterrassa.data.core.repository.jpa.auth.model.PasswordRecoveryTokenJPA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordRecoveryTokenRepositoryHelper extends JpaRepository<PasswordRecoveryTokenJPA, UUID> {
    Optional<PasswordRecoveryTokenJPA> findByTokenHashAndConsumedFalseAndExpiresAtAfter(
            String tokenHash, ZonedDateTime now);

    @Modifying
    @Transactional
    @Query("""
            update PasswordRecoveryTokenJPA token
               set token.consumed = true
             where token.id = :id
               and token.consumed = false
               and token.expiresAt > :now
            """)
    int consumeIfActive(@Param("id") UUID id, @Param("now") ZonedDateTime now);

    @Modifying
    @Transactional
    @Query("""
            update PasswordRecoveryTokenJPA token
               set token.consumed = true
             where token.userId = :userId
               and token.consumed = false
               and token.expiresAt > :now
            """)
    int consumeAllForUser(@Param("userId") UUID userId, @Param("now") ZonedDateTime now);
}
