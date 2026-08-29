package org.cttelsamicsterrassa.data.core.domain.game.repository;

import org.cttelsamicsterrassa.data.core.domain.game.model.Game;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

/**
 * Persistence port for the games of a match.
 */
public interface GameRepository {
    List<Game> findGamesByMatchId(UUID matchId);
    List<Game> findGamesByMatchIds(Collection<UUID> matchIds);
    void saveGames(List<Game> games);
}
