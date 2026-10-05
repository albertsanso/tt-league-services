package org.cttelsamicsterrassa.data.pipeline.core.alert;

import java.time.Duration;

/**
 * Thresholds of the alert rules: a match is unreported once {@code unreportedAfter} passed since its date, a source
 * has no recent success after {@code noSuccessWindow}, and closed match days older than {@code closedLookback} do
 * not raise.
 */
public record AlertSettings(Duration unreportedAfter, Duration noSuccessWindow, Duration closedLookback) {

    public AlertSettings {
        AlertChecks.positive(unreportedAfter, "unreportedAfter");
        AlertChecks.positive(noSuccessWindow, "noSuccessWindow");
        AlertChecks.positive(closedLookback, "closedLookback");
    }
}
