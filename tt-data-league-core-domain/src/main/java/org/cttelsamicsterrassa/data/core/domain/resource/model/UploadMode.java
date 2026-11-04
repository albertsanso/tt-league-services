package org.cttelsamicsterrassa.data.core.domain.resource.model;

/**
 * Upload mode declared by an import manifest. {@link #SNAPSHOT} replaces the stored season folder;
 * {@link #DELTA} merges into it without deleting stored files.
 */
public enum UploadMode {

    SNAPSHOT,
    DELTA;

    /**
     * Parses the manifest {@code mode} value. The accepted values are the exact lowercase
     * {@code "snapshot"} and {@code "delta"}; anything else is rejected instead of guessed.
     *
     * @param value the raw manifest value
     * @return the matching mode
     * @throws IllegalArgumentException when the value is null or not one of the two accepted values
     */
    public static UploadMode fromManifestValue(String value) {
        if ("snapshot".equals(value)) {
            return SNAPSHOT;
        }
        if ("delta".equals(value)) {
            return DELTA;
        }
        throw new IllegalArgumentException("manifest.json mode must be one of snapshot, delta");
    }
}