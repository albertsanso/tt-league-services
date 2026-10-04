package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactContent;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStoreException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.StoredArtifact;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Keeps artifacts in memory and hashes for real. */
public class InMemoryArtifactStore implements ArtifactStore {

    private final Map<String, byte[]> files = new HashMap<>();

    @Override
    public synchronized StoredArtifact store(String storageKey, InputStream content) {
        if (files.containsKey(storageKey)) {
            throw new ArtifactStoreException("Artifact already exists: " + storageKey);
        }
        try {
            byte[] bytes = content.readAllBytes();
            files.put(storageKey, bytes);
            return new StoredArtifact(storageKey, sha256(bytes), bytes.length);
        } catch (IOException e) {
            throw new ArtifactStoreException("Could not store " + storageKey, e);
        }
    }

    @Override
    public synchronized ArtifactContent content(String storageKey) {
        byte[] bytes = files.get(storageKey);
        if (bytes == null) {
            throw new ArtifactStoreException("Unknown artifact: " + storageKey);
        }
        return new ArtifactContent() {
            @Override
            public long size() {
                return bytes.length;
            }

            @Override
            public InputStream open() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    @Override
    public synchronized boolean exists(String storageKey) {
        return files.containsKey(storageKey);
    }

    @Override
    public synchronized void delete(String storageKey) {
        files.remove(storageKey);
    }

    public synchronized byte[] bytes(String storageKey) {
        return files.get(storageKey);
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
