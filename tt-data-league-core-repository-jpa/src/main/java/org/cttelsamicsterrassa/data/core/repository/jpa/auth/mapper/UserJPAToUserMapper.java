package org.cttelsamicsterrassa.data.core.repository.jpa.auth.mapper;

import org.cttelsamicsterrassa.data.core.domain.auth.model.User;
import org.cttelsamicsterrassa.data.core.repository.jpa.auth.model.UserJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class UserJPAToUserMapper implements Function<UserJPA, User> {
    @Override
    public User apply(UserJPA userJPA) {
        if (userJPA == null) {
            return null;
        }

        return User.createExisting(
                userJPA.getId(),
                userJPA.getCreatedAt(),
                userJPA.getUsername(),
                userJPA.getEmail(),
                userJPA.getPasswordHash(),
                userJPA.isActive()
        );
    }
}
