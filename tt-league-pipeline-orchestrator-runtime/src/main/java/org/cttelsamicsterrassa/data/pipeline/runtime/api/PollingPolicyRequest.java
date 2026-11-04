package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;

/**
 * Full replacement of a source's polling settings. {@code version} is the stored override version, or 0 when the
 * source has no override yet. Every field is required; durations are ISO-8601 (for example {@code PT2H}).
 */
public record PollingPolicyRequest(
        @NotNull Duration matchDay,
        @NotNull Duration matchDayStartOffset,
        @NotNull Duration dayAfter,
        @NotNull Duration daysTwoToSeven,
        @NotNull Duration open,
        @NotNull Duration overdue,
        @NotNull Integer overdueStopAfterDays,
        @NotNull Duration fullRefresh,
        @NotNull Integer noChangeThreshold,
        @NotNull Integer recentMatchDays,
        @NotNull Long version) {

    /** Throws {@link IllegalArgumentException} when the settings are invalid. */
    PollingSettings toSettings() {
        return new PollingSettings(matchDay, matchDayStartOffset, dayAfter, daysTwoToSeven, open, overdue,
                overdueStopAfterDays, fullRefresh, noChangeThreshold, recentMatchDays);
    }
}
