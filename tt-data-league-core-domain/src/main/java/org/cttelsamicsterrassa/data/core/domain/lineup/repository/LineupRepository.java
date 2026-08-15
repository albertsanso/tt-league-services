package org.cttelsamicsterrassa.data.core.domain.lineup.repository;

import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;

import java.util.List;
import java.util.UUID;

/**
 * Persistence port for the lineup entries of a match.
 */
public interface LineupRepository {
    List<Lineup> findLineupsByMatchId(UUID matchId);
    void saveLineups(List<Lineup> lineups);
}
