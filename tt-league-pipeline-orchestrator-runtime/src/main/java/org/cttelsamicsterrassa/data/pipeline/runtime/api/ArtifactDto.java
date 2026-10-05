package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;

/** Artifact metadata; the storage key is internal and not exposed. {@code purgedAt} is set once the file was deleted. */
public record ArtifactDto(String kind, String sha256, long sizeBytes, Instant createdAt, Instant purgedAt) {
}
