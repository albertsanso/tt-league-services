package org.cttelsamicsterrassa.data.core.repository.jpa.auth.mapper;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.repository.jpa.auth.model.UserJPA;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class UserToUserJPAMapper implements Function<User, UserJPA> {
    @Override
    public UserJPA apply(User user) {
        if (user == null) {
            return null;
        }

        UserJPA userJPA = new UserJPA();
        userJPA.setId(user.getId());
        userJPA.setUsername(user.getUsername());
        userJPA.setEmail(user.getEmail());
        userJPA.setPasswordHash(user.getPasswordHash());
        userJPA.setCreatedAt(user.getCreatedAt());
        userJPA.setActive(user.isActived());
        userJPA.setRoles(user.getRoles());
        return userJPA;
    }
}
