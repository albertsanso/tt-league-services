package org.cttelsamicsterrassa.data.pipeline.core.tracker.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayEvent;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayFacets;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayPage;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayQuery;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.SourceSeason;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistence port of the match-day tracker. */
public interface MatchDayRepository {

    Optional<MatchDay> findById(UUID matchDayId);

    List<MatchDay> findBySourceAndSeason(PipelineSource source, String season);

    /** Matches of the given match days; an empty collection yields an empty list. */
    List<MatchTracking> findMatches(Collection<UUID> matchDayIds);

    Optional<MatchTracking> findMatch(UUID matchId);

    /** Oldest first. */
    List<MatchDayEvent> findEvents(UUID matchDayId);

    /** Source and season pairs with at least one match day that is not CLOSED. */
    Set<SourceSeason> findSourceSeasonsWithUnclosedDays();

    /** Match days of every source and season in the given state. Read-only; used by the alerts. */
    List<MatchDay> findByState(MatchDayState state);

    /** CLOSED match days of every source and season with {@code closedAt >= since}. Read-only; used by the alerts. */
    List<MatchDay> findClosedSince(Instant since);

    MatchDayPage query(MatchDayQuery query);

    /**
     * Distinct sorted filter values. {@code seasons} honours only {@code source}; {@code competitions} and
     * {@code phases} honour both {@code source} and {@code season} (null means all). Absent phases are left out.
     */
    MatchDayFacets facets(PipelineSource source, String season);

    /**
     * Writes the change set atomically: days first, then matches, removals and events. A day or match that exists is
     * updated and its stored version must equal the aggregate version (the stored one then increases by one); one
     * that does not exist is inserted. Throws {@link StaleMatchDayException} on a version conflict or when another
     * writer created the same match day key.
     */
    void apply(MatchDayChangeSet changes);
}
