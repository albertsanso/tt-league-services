package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ScopeType;

/** Body of {@code POST /api/pipeline/runs}. There is no default season, source or scope. */
public record TriggerRunRequest(
        @NotBlank @Schema(description = "RFETM, BCNESA, FCTT or ALL (one run per source)") String source,
        @NotBlank @Schema(description = "Season such as 2025-2026", example = "2025-2026") String season,
        @NotNull ScopeType scopeType,
        @Schema(description = "Required for GROUP, rejected otherwise") List<ScopeFilterDto> filters,
        @Schema(description = "Bypass the ingest no-change skip") boolean force) {
}
