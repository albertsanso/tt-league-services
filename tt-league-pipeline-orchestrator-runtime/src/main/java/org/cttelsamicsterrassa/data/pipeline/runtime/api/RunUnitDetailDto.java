package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.UnitRetryEligibility;

/**
 * A unit with its own steps and artifacts. {@code storageFolder} is the relative artifact folder of the unit (the
 * parent of its stored package), {@code packageUrl} the download path of that package (null when there is no unpurged
 * ZIP), {@code retry} the decision of the core {@code UnitRetryRules} and {@code retriedBy} the UNIT_RETRY runs
 * created for the unit.
 */
public record RunUnitDetailDto(
        @JsonUnwrapped RunUnitDto unit,
        List<StepDto> steps,
        List<ArtifactDto> artifacts,
        String storageFolder,
        String packageUrl,
        Retry retry,
        List<RetryRef> retriedBy) {

    /** Whether the unit can be retried on its own; {@code reason} names why not (null when eligible). */
    public record Retry(boolean eligible, String reason) {

        static Retry from(UnitRetryEligibility eligibility) {
            return new Retry(eligibility.allowed(), eligibility.code());
        }
    }

    public record RetryRef(UUID runId, String status) {
    }
}
