package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.io.InputStream;

public interface ArtifactStore {

    /**
     * Writes the content, computing SHA-256 and size on the way. The file becomes visible only when complete.
     * Throws {@link ArtifactStoreException} for an existing key or an I/O failure.
     */
    StoredArtifact store(String storageKey, InputStream content);

    ArtifactContent content(String storageKey);

    boolean exists(String storageKey);

    /** Idempotent. */
    void delete(String storageKey);
}
