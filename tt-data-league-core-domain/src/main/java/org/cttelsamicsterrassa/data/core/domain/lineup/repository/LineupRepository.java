package org.cttelsamicsterrassa.data.core.domain.lineup.repository;

import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;

import java.util.List;
import java.util.UUID;

/**
 * Persistence port for the lineup entries of a match.
 */
public interface LineupRepository {
    List<Lineup> findLineupsByMatchId(UUID matchId);

    default List<Lineup> findAllLineupsByPlayerSeasonIds(List<UUID> playerSeasonIds) {
        return List.of();
    }

    default List<Lineup> findAllLineupsByPlayerSeasonIds(List<UUID> playerSeasonIds, int maxResults) {
        return findAllLineupsByPlayerSeasonIds(playerSeasonIds).stream().limit(maxResults).toList();
    }

    void saveLineups(List<Lineup> lineups);
}
