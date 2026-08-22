package org.cttelsamicsterrassa.data.core.repository.jpa.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Table(
        name = "AppUser",
        indexes = {
                @Index(name = "idx_user_username", columnList = "username", unique = true),
                @Index(name = "idx_user_email", columnList = "email", unique = true)
        }
)
public class UserJPA {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @ElementCollection
    @CollectionTable(name = "AppUserRole", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Set<UserRole> roles = EnumSet.of(UserRole.PRACTITIONER);

}
