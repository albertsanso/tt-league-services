package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import java.util.Objects;

/** Whether a run can be replayed; {@code code} names the reason when it cannot, and is null when allowed. */
public record ReplayEligibility(boolean allowed, String code) {

    public static final String RUN_ACTIVE = "RUN_ACTIVE";
    public static final String NO_PACKAGE = "NO_PACKAGE";
    public static final String ARTIFACT_PURGED = "ARTIFACT_PURGED";

    public ReplayEligibility {
        if (allowed != (code == null)) {
            throw new IllegalArgumentException("code is required exactly when the replay is not allowed");
        }
    }

    public static ReplayEligibility yes() {
        return new ReplayEligibility(true, null);
    }

    public static ReplayEligibility no(String code) {
        return new ReplayEligibility(false, Objects.requireNonNull(code, "code is required"));
    }
}
