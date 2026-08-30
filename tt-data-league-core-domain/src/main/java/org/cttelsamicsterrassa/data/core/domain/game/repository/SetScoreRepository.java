package org.cttelsamicsterrassa.data.core.domain.game.repository;

import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

/**
 * Persistence port for the set scores of a game.
 */
public interface SetScoreRepository {
    default List<SetScore> findSetScoresByGameIds(Collection<UUID> gameIds) {
        return List.of();
    }
    void saveSetScores(List<SetScore> setScores);
}
