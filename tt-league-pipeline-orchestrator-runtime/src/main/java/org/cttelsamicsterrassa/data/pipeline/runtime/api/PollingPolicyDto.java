package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Duration;
import java.time.Instant;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettingsProvider.Effective;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/**
 * The effective polling settings of a source. {@code overridden} is false when the configured defaults apply; then
 * {@code version} is 0 and {@code updatedBy}/{@code updatedAt} are null. Durations are ISO-8601.
 */
public record PollingPolicyDto(
        PipelineSource source,
        Duration matchDay,
        Duration matchDayStartOffset,
        Duration dayAfter,
        Duration daysTwoToSeven,
        Duration open,
        Duration overdue,
        int overdueStopAfterDays,
        Duration fullRefresh,
        int noChangeThreshold,
        int recentMatchDays,
        boolean overridden,
        long version,
        String updatedBy,
        Instant updatedAt) {

    static PollingPolicyDto from(Effective effective) {
        PollingSettings settings = effective.settings();
        return new PollingPolicyDto(effective.source(), settings.matchDay(), settings.matchDayStartOffset(),
                settings.dayAfter(), settings.daysTwoToSeven(), settings.open(), settings.overdue(),
                settings.overdueStopAfterDays(), settings.fullRefresh(), settings.noChangeThreshold(),
                settings.recentMatchDays(), effective.overridden(), effective.version(), effective.updatedBy(), effective.updatedAt());
    }
}
