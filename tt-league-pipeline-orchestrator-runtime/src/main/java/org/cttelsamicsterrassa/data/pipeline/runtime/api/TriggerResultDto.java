package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/** Outcome for one source: CREATED, QUEUED, REJECTED or UNAVAILABLE. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TriggerResultDto(
        String source, String outcome, String code, String message, RunSummaryDto run, UUID activeRunId) {
}
