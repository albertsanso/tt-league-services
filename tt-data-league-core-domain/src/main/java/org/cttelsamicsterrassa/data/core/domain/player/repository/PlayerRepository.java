package org.cttelsamicsterrassa.data.core.domain.player.repository;

import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link Player}, the season-independent player identity.
 */
public interface PlayerRepository {
    Optional<Player> findPlayerById(UUID id);
    Optional<Player> findPlayerByName(String name);
    Optional<Player> findPlayerBySourceAndName(ImportSource source, String name);
    void savePlayer(Player player);
}
