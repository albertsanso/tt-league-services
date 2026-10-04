package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.util.UUID;

public record ImportSubmission(UUID importJobId, String status, boolean created) {
}
