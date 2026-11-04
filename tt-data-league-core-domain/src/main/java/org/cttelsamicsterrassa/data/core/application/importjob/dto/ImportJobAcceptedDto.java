package org.cttelsamicsterrassa.data.core.application.importjob.dto;

import java.util.UUID;

/**
 * Response to an import job submission. {@code created} is false when an existing job for the same content was
 * returned instead of a new one.
 */
public record ImportJobAcceptedDto(UUID importJobId, String status, boolean created) {
}
