package org.cttelsamicsterrassa.data.core.domain.game.repository;

import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;

import java.util.List;

/**
 * Persistence port for the set scores of a game.
 */
public interface SetScoreRepository {
    void saveSetScores(List<SetScore> setScores);
}
