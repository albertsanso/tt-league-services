package org.cttelsamicsterrassa.data.core.domain.auth.repository;

import org.cttelsamicsterrassa.data.core.domain.auth.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findAll();
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    void save(User user);
}
