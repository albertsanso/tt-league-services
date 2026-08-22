package org.cttelsamicsterrassa.data.core.repository.jpa.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "PasswordRecoveryToken",
        indexes = {
                @Index(name = "idx_recovery_token_hash", columnList = "token_hash", unique = true),
                @Index(name = "idx_recovery_expiry", columnList = "expires_at")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_recovery_token_hash", columnNames = "token_hash")
)
public class PasswordRecoveryTokenJPA {
    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(nullable = false)
    private boolean consumed;
}
