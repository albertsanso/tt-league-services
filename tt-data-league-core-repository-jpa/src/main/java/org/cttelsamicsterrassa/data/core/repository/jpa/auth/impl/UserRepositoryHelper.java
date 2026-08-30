package org.cttelsamicsterrassa.data.core.repository.jpa.auth.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.auth.model.UserJPA;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryHelper extends JpaRepository<UserJPA, UUID> {
    Optional<UserJPA> findByUsername(String username);
    Optional<UserJPA> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM UserJPA u WHERE "
            + "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<UserJPA> findBySearch(@Param("search") String search, Pageable pageable);

    @Query("SELECT u FROM UserJPA u WHERE u.active = :active")
    Page<UserJPA> findByActive(@Param("active") Boolean active, Pageable pageable);

    @Query("SELECT u FROM UserJPA u WHERE "
            + "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "AND u.active = :active")
    Page<UserJPA> findBySearchAndActive(
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable);

    @Query("SELECT COUNT(u) FROM UserJPA u JOIN u.roles r WHERE r = :role AND u.active = true")
    long countActiveByRole(@Param("role") UserRole role);
}
