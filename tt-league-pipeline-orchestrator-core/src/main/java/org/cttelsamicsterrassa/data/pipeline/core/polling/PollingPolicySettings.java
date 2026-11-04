package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.time.Instant;
import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** A stored per-source override of the polling settings, versioned for optimistic updates. */
public record PollingPolicySettings(
        PipelineSource source, PollingSettings settings, long version, String updatedBy, Instant updatedAt) {

    public PollingPolicySettings {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(settings, "settings is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        Objects.requireNonNull(updatedBy, "updatedBy is required");
        if (updatedBy.isBlank() || updatedBy.length() > 128) {
            throw new IllegalArgumentException("updatedBy must be 1 to 128 characters");
        }
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
