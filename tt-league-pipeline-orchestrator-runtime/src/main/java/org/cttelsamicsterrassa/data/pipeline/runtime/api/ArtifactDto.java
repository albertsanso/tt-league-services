package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Artifact metadata; the storage key is internal and not exposed (the unit detail shows its folder). {@code purgedAt}
 * is set once the file was deleted.
 */
public record ArtifactDto(
        UUID unitId, String kind, String sha256, long sizeBytes, Instant createdAt, Instant purgedAt) {
}
