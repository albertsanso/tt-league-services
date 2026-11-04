package org.cttelsamicsterrassa.data.core.domain.match.repository;

import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the manual overdue mark (FEAT-00092).
 *
 * <p>The mark is operator input, separate from import data: no import processor, reconciler or
 * consolidation code reads or writes it. A row stores an operator decision, not a computed state;
 * {@code save} leaves an existing row untouched.</p>
 */
public interface MatchOverdueMarkRepository {

    Optional<MatchOverdueMark> findByMatchId(UUID matchId);

    /**
     * Returns the marks whose match id is in {@code matchIds}. An empty input returns an empty list
     * and issues no query.
     */
    List<MatchOverdueMark> findByMatchIds(Collection<UUID> matchIds);

    void save(MatchOverdueMark mark);

    /**
     * Removes the mark for {@code matchId}, returning {@code true} when a row was removed and
     * {@code false} when no row existed.
     */
    boolean deleteByMatchId(UUID matchId);
}