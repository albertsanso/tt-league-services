package org.cttelsamicsterrassa.data.pipeline.runtime.artifact;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactContent;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStoreException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.StoredArtifact;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stores artifacts under a root directory. A file becomes visible only through an atomic move of a completed
 * temporary file, so a crash never leaves a partial artifact at its key.
 */
public final class FileSystemArtifactStore implements ArtifactStore {

    private static final String TEMP_DIR = ".tmp";

    private final Path root;

    /** Fails with {@link IllegalStateException} when the root is missing, not a directory or not writable. */
    public FileSystemArtifactStore(Path root) {
        Path absolute = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw new IllegalStateException("Artifact directory does not exist or is not a directory: " + absolute);
        }
        if (!Files.isWritable(absolute)) {
            throw new IllegalStateException("Artifact directory is not writable: " + absolute);
        }
        this.root = absolute;
        cleanTemporaryFiles();
    }

    @Override
    public StoredArtifact store(String storageKey, InputStream content) {
        Path target = resolve(storageKey);
        Path temp = root.resolve(TEMP_DIR).resolve(UUID.randomUUID() + ".part");
        try {
            Files.createDirectories(temp.getParent());
            MessageDigest digest = digest();
            long size;
            try (DigestInputStream in = new DigestInputStream(content, digest);
                    OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)) {
                size = in.transferTo(out);
            }
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.createDirectories(target.getParent());
            publish(temp, target, storageKey);
            return new StoredArtifact(storageKey, HexFormat.of().formatHex(digest.digest()), size);
        } catch (IOException e) {
            throw new ArtifactStoreException("Could not store artifact " + storageKey + ": " + e.getMessage(), e);
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * ATOMIC_MOVE replaces an existing target on most file systems, so the existence check and the move are
     * serialized: this process is the only writer of the artifact directory.
     */
    private synchronized void publish(Path temp, Path target, String storageKey) throws IOException {
        if (Files.exists(target)) {
            throw new ArtifactStoreException("Artifact already exists: " + storageKey);
        }
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public ArtifactContent content(String storageKey) {
        Path file = resolve(storageKey);
        if (!Files.isRegularFile(file)) {
            throw new ArtifactStoreException("Unknown artifact: " + storageKey);
        }
        return new ArtifactContent() {
            @Override
            public long size() {
                try {
                    return Files.size(file);
                } catch (IOException e) {
                    throw new ArtifactStoreException("Could not read the size of " + storageKey, e);
                }
            }

            @Override
            public InputStream open() {
                try {
                    return Files.newInputStream(file);
                } catch (IOException e) {
                    throw new ArtifactStoreException("Could not open artifact " + storageKey, e);
                }
            }
        };
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new ArtifactStoreException("Could not delete artifact " + storageKey, e);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new ArtifactStoreException("Storage key must not be blank");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new ArtifactStoreException("Storage key escapes the artifact directory: " + storageKey);
        }
        if (resolved.startsWith(root.resolve(TEMP_DIR))) {
            throw new ArtifactStoreException("Storage key is reserved: " + storageKey);
        }
        return resolved;
    }

    private void cleanTemporaryFiles() {
        Path temp = root.resolve(TEMP_DIR);
        if (!Files.isDirectory(temp)) {
            return;
        }
        try (Stream<Path> leftovers = Files.list(temp)) {
            for (Path leftover : leftovers.toList()) {
                Files.deleteIfExists(leftover);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not clean temporary artifact files in " + temp, e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // the part file is cleaned at the next startup
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
