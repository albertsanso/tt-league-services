package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "pipeline", name = "run_artifact")
class RunArtifactEntity {

    @Id
    UUID id;

    @Column(name = "run_id", nullable = false)
    UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    ArtifactKind kind;

    @Column(name = "storage_key", nullable = false, length = 512)
    String storageKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    String sha256;

    @Column(name = "size_bytes", nullable = false)
    long sizeBytes;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "purged_at")
    Instant purgedAt;

    protected RunArtifactEntity() {
    }

    RunArtifactEntity(UUID id) {
        this.id = id;
    }
}
