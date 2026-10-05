package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;

/** A tracked match with the source, season, competition and state of its match day. Never carries the result. */
public record MatchFacts(
        UUID matchId,
        UUID matchDayId,
        PipelineSource source,
        String season,
        String competition,
        MatchDayState dayState,
        TrackedMatchStatus status,
        Instant matchDateTime,
        Instant firstSeenAt,
        Instant reportedAt,
        boolean ignored) {}
