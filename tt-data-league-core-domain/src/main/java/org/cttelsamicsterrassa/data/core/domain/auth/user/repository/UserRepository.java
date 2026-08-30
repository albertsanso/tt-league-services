package org.cttelsamicsterrassa.data.core.domain.auth.user.repository;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserFilter;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserPage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findAll();
    UserPage findPage(UserFilter filter);
    long countActiveAdmins();
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    void save(User user);
    void delete(UUID id);
}
