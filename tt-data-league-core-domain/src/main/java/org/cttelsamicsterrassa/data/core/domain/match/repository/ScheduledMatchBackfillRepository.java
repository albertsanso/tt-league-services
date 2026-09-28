package org.cttelsamicsterrassa.data.core.domain.match.repository;

import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillWriteResult;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Port for the FEAT-00078 backfill: marking legacy empty and "decided 0-0" {@code PLAYED} matches
 * as {@link org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus#SCHEDULED}. Kept
 * separate from {@link MatchRepository} so this port does not collide with unrelated match-write
 * additions and so existing {@link MatchRepository} implementations do not need a new method.
 */
public interface ScheduledMatchBackfillRepository {

    /**
     * Candidates per the backfill rule, ordered by competition, group, round, match id. Read-only.
     */
    List<ScheduledMatchBackfillCandidate> findScheduledBackfillCandidates(ImportSource source, Season season);

    /**
     * Marks the given candidates SCHEDULED in one transaction: deletes their doubles pairs, set scores,
     * games and lineups, nulls games/sets won, sets status. Re-checks the rule, the source and the
     * season for every id and fails (IllegalStateException, nothing written) when any id is no longer
     * a candidate.
     */
    ScheduledMatchBackfillWriteResult markScheduled(ImportSource source, Season season, Collection<UUID> matchIds);
}
