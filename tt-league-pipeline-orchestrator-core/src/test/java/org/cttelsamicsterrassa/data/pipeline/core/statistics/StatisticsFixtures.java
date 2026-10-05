package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;

/** Small builders shared by the statistics tests. */
final class StatisticsFixtures {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final StatisticsSettings SETTINGS = new StatisticsSettings(MADRID, LocalTime.of(0, 30), 31);

    private StatisticsFixtures() {}

    static Instant at(String isoInstant) {
        return Instant.parse(isoInstant);
    }

    /** A reported match that arrived while tracked: first seen at {@code seen}, reported at {@code reported}. */
    static MatchFacts arrival(PipelineSource source, String competition, Instant played, Instant seen, Instant reported) {
        return new MatchFacts(UUID.randomUUID(), UUID.randomUUID(), source, "2026-2027", competition,
                MatchDayState.OPEN, TrackedMatchStatus.REPORTED, played, seen, reported, false);
    }

    static MatchFacts pending(PipelineSource source, TrackedMatchStatus status, Instant played) {
        return new MatchFacts(UUID.randomUUID(), UUID.randomUUID(), source, "2026-2027", "TERCERA",
                MatchDayState.OPEN, status, played, played.minus(Duration.ofDays(3)), null, false);
    }

    static MatchFacts withDayState(MatchFacts match, MatchDayState state) {
        return new MatchFacts(match.matchId(), match.matchDayId(), match.source(), match.season(),
                match.competition(), state, match.status(), match.matchDateTime(), match.firstSeenAt(),
                match.reportedAt(), match.ignored());
    }

    static MatchFacts ignored(MatchFacts match) {
        return new MatchFacts(match.matchId(), match.matchDayId(), match.source(), match.season(),
                match.competition(), match.dayState(), match.status(), match.matchDateTime(), match.firstSeenAt(),
                match.reportedAt(), true);
    }

    static RunFacts run(PipelineSource source, RunStatus status, Instant finishedAt) {
        return new RunFacts(UUID.randomUUID(), source, status, finishedAt.minusSeconds(60), finishedAt);
    }

    static StepFacts ingestStep(PipelineSource source, String outcome, Instant finishedAt, IngestHealth health) {
        return new StepFacts(UUID.randomUUID(), source, StepKind.INGEST, StepStatus.SUCCEEDED, outcome,
                finishedAt.minusSeconds(30), finishedAt, health);
    }
}
