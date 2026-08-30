package org.cttelsamicsterrassa.data.core.domain.shared.model;

import java.time.Instant;
import java.util.UUID;

public record ImportJob(UUID id, ImportSource source, String mappingVersion,
                        ImportJobStatus status, int processed, int total,
                        long errors, long warnings, Instant createdAt, Instant updatedAt) {
}
