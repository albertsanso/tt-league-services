package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import java.util.Objects;

/** Whether a unit can be retried; {@code code} names the reason when it cannot, and is null when allowed. */
public record UnitRetryEligibility(boolean allowed, String code) {

    public static final String RUN_ACTIVE = "RUN_ACTIVE";
    public static final String UNIT_NOT_RETRYABLE = "UNIT_NOT_RETRYABLE";

    public UnitRetryEligibility {
        if (allowed != (code == null)) {
            throw new IllegalArgumentException("code is required exactly when the retry is not allowed");
        }
    }

    public static UnitRetryEligibility yes() {
        return new UnitRetryEligibility(true, null);
    }

    public static UnitRetryEligibility no(String code) {
        return new UnitRetryEligibility(false, Objects.requireNonNull(code, "code is required"));
    }
}
