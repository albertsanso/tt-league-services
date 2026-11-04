package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import org.cttelsamicsterrassa.data.pipeline.core.trigger.ReplayEligibility;

/** Whether the run can be replayed, as decided by the core {@code ReplayRules}; {@code code} names the reason if not. */
public record ReplayDto(boolean allowed, String code) {

    static ReplayDto from(ReplayEligibility eligibility) {
        return new ReplayDto(eligibility.allowed(), eligibility.code());
    }
}
