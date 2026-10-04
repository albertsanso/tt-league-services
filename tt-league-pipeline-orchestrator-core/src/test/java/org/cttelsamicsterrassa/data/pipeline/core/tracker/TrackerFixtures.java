package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Small builders shared by the tracker tests. */
final class TrackerFixtures {

    static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    static final LocalDate TODAY = LocalDate.parse("2026-10-04");

    private TrackerFixtures() {
    }

    static MatchDayKey key(int round) {
        return new MatchDayKey(PipelineSource.FCTT, "2026-2027", "TERCERA", 1, "1a Fase", round);
    }

    static MatchDay day(MatchDayWindow window) {
        return MatchDay.create(UUID.randomUUID(), key(1), window, NOW);
    }

    static MatchDay dayStarted() {
        return day(new MatchDayWindow(TODAY.minusDays(1), TODAY, 2));
    }

    static MatchDay openDay() {
        return dayStarted().open(NOW);
    }

    static MatchTracking match(UUID dayId, TrackedMatchStatus status) {
        Instant reported = status == TrackedMatchStatus.REPORTED ? NOW : null;
        return MatchTracking.first(UUID.randomUUID(), dayId, status, NOW, "Home", "Away", NOW, reported, null);
    }
}
