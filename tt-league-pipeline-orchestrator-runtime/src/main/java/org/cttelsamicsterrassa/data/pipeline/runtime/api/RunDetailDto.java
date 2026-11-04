package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/**
 * The run summary plus its units with their own steps, artifacts and import counters, every step attempt and artifact of
 * the run, the import report summed over the units, the issues and whether the run can be replayed.
 */
public record RunDetailDto(
        @JsonUnwrapped RunSummaryDto run,
        List<RunUnitDetailDto> units,
        List<StepDto> steps,
        List<ArtifactDto> artifacts,
        ImportReportDto importReport,
        List<String> issues,
        ReplayDto replay) {
}
