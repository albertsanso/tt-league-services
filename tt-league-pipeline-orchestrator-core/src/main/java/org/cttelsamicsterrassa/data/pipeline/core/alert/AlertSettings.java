package org.cttelsamicsterrassa.data.pipeline.core.alert;

import java.time.Duration;
import java.util.Set;

/**
 * Thresholds of the alert rules: a match is unreported once {@code unreportedAfter} passed since its date, a source
 * has no recent success after {@code noSuccessWindow}, and closed match days older than {@code closedLookback} do
 * not raise. {@code unitKeys} limits the unit failure alerts to those unit keys; empty means every unit.
 */
public record AlertSettings(
        Duration unreportedAfter, Duration noSuccessWindow, Duration closedLookback, Set<String> unitKeys) {

    /** Settings that raise unit failure alerts for every unit. */
    public AlertSettings(Duration unreportedAfter, Duration noSuccessWindow, Duration closedLookback) {
        this(unreportedAfter, noSuccessWindow, closedLookback, Set.of());
    }

    public AlertSettings {
        AlertChecks.positive(unreportedAfter, "unreportedAfter");
        AlertChecks.positive(noSuccessWindow, "noSuccessWindow");
        AlertChecks.positive(closedLookback, "closedLookback");
        unitKeys = Set.copyOf(AlertChecks.required(unitKeys, "unitKeys"));
    }
}
