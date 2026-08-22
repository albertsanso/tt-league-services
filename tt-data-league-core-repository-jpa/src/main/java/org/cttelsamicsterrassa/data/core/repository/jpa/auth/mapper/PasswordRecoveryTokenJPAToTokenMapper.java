package org.cttelsamicsterrassa.data.core.repository.jpa.auth.mapper;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.PasswordRecoveryToken;
import org.cttelsamicsterrassa.data.core.repository.jpa.auth.model.PasswordRecoveryTokenJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class PasswordRecoveryTokenJPAToTokenMapper implements Function<PasswordRecoveryTokenJPA, PasswordRecoveryToken> {
    @Override
    public PasswordRecoveryToken apply(PasswordRecoveryTokenJPA entity) {
        if (entity == null) {
            return null;
        }

        return new PasswordRecoveryToken(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.isConsumed());
    }
}
