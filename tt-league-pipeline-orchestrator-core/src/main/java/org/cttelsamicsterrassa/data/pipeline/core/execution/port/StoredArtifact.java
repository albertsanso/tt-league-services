package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.util.Objects;

public record StoredArtifact(String storageKey, String sha256, long sizeBytes) {

    public StoredArtifact {
        Objects.requireNonNull(storageKey, "storageKey is required");
        Objects.requireNonNull(sha256, "sha256 is required");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }
}
