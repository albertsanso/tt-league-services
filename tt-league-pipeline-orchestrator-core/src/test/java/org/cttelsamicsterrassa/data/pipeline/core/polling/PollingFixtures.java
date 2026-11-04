package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;

/** Small builders shared by the polling tests. Local times are in {@link #ZONE}. */
final class PollingFixtures {

    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    static final String SEASON = "2026-2027";

    private PollingFixtures() {
    }

    /** The instant of a local date-time such as {@code 2026-10-04T10:00}. */
    static Instant at(String local) {
        return LocalDateTime.parse(local).atZone(ZONE).toInstant();
    }

    static MatchTracking match(TrackedMatchStatus status, Instant dateTime) {
        Instant seen = at("2026-09-01T10:00");
        Instant reported = status == TrackedMatchStatus.REPORTED ? seen : null;
        return MatchTracking.first(UUID.randomUUID(), UUID.randomUUID(), status, dateTime, "Home", "Away", seen,
                reported, null);
    }

    static MatchTracking match(TrackedMatchStatus status, String local) {
        return match(status, local == null ? null : at(local));
    }
}
