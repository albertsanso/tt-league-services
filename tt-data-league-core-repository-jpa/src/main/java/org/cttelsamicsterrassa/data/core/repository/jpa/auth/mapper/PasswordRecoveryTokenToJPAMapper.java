package org.cttelsamicsterrassa.data.core.repository.jpa.auth.mapper;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.PasswordRecoveryToken;
import org.cttelsamicsterrassa.data.core.repository.jpa.auth.model.PasswordRecoveryTokenJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class PasswordRecoveryTokenToJPAMapper implements Function<PasswordRecoveryToken, PasswordRecoveryTokenJPA> {
    @Override
    public PasswordRecoveryTokenJPA apply(PasswordRecoveryToken token) {
        if (token == null) {
            return null;
        }

        PasswordRecoveryTokenJPA entity = new PasswordRecoveryTokenJPA();
        entity.setId(token.id());
        entity.setUserId(token.userId());
        entity.setTokenHash(token.tokenHash());
        entity.setCreatedAt(token.createdAt());
        entity.setExpiresAt(token.expiresAt());
        entity.setConsumed(token.consumed());
        return entity;
    }
}
