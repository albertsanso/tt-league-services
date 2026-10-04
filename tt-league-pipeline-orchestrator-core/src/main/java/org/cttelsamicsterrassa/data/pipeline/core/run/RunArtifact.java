package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record RunArtifact(
        UUID id, UUID runId, ArtifactKind kind, String storageKey, String sha256, long sizeBytes, Instant createdAt) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");
    private static final Pattern SEPARATOR = Pattern.compile("[/\\\\]");

    public RunArtifact {
        Checks.required(id, "id");
        Checks.required(runId, "runId");
        Checks.required(kind, "kind");
        Checks.nonBlankMax(storageKey, "storageKey", 512);
        if (storageKey.startsWith("/") || storageKey.startsWith("\\") || DRIVE_PREFIX.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("storageKey must be a relative path: " + storageKey);
        }
        for (String segment : SEPARATOR.split(storageKey, -1)) {
            if (segment.equals("..")) {
                throw new IllegalArgumentException("storageKey must not contain '..' segments: " + storageKey);
            }
        }
        Checks.required(sha256, "sha256");
        if (!SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
        Checks.nonNegative(sizeBytes, "sizeBytes");
        Checks.required(createdAt, "createdAt");
    }
}
