package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/**
 * The run summary plus every step attempt, the artifacts, the import report, the issues and whether the run can be
 * replayed.
 */
public record RunDetailDto(
        @JsonUnwrapped RunSummaryDto run,
        List<StepDto> steps,
        List<ArtifactDto> artifacts,
        ImportReportDto importReport,
        List<String> issues,
        ReplayDto replay) {
}
