package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;

/** A tracked match: operational state only, never the result (the platform owns it). */
public record TrackedMatchDto(
        UUID matchId,
        String status,
        Instant matchDateTime,
        String homeTeamName,
        String awayTeamName,
        Instant firstSeenAt,
        Instant statusChangedAt,
        Instant lastSeenAt,
        Instant reportedAt,
        UUID reportedRunId,
        Instant ignoredAt,
        String ignoredBy) {

    static TrackedMatchDto from(MatchTracking match) {
        return new TrackedMatchDto(match.matchId(), match.status().name(), match.matchDateTime(),
                match.homeTeamName(), match.awayTeamName(), match.firstSeenAt(), match.statusChangedAt(),
                match.lastSeenAt(), match.reportedAt(), match.reportedRunId(), match.ignoredAt(), match.ignoredBy());
    }
}
