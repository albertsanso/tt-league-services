package org.cttelsamicsterrassa.data.core.domain.player.repository;

import org.cttelsamicsterrassa.data.core.domain.player.model.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository {
    Optional<Player> findPlayerById(UUID id);

    /**
     * Finds a canonical player using the exact, case-sensitive display name.
     */
    Optional<Player> findPlayerByExactName(String name);
    Optional<Player> findFirstPlayerByNameFragments(List<String> fragments);

    default Optional<Player> findPlayerByName(String name) {
        return findPlayerByExactName(name);
    }

    void savePlayer(Player player);

    void deletePlayerById(UUID id);
}
