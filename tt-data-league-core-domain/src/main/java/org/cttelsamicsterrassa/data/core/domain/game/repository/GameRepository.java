package org.cttelsamicsterrassa.data.core.domain.game.repository;

import org.cttelsamicsterrassa.data.core.domain.game.model.Game;

import java.util.List;
import java.util.UUID;

/**
 * Persistence port for the games of a match.
 */
public interface GameRepository {
    List<Game> findGamesByMatchId(UUID matchId);
    void saveGames(List<Game> games);
}
