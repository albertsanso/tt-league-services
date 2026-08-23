package org.cttelsamicsterrassa.data.load.shared.player;

import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;

import java.util.Objects;

/**
 * Resolves canonical player identities by exact display name only.
 */
public class CanonicalPlayerResolver {
    private final PlayerRepository playerRepository;

    public CanonicalPlayerResolver(PlayerRepository playerRepository) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");
    }

    public Player resolveOrCreate(String canonicalName) {
        Objects.requireNonNull(canonicalName, "canonicalName");
        return playerRepository.findPlayerByExactName(canonicalName)
                .orElseGet(() -> {
                    Player player = Player.createNew(canonicalName);
                    playerRepository.savePlayer(player);
                    return player;
                });
    }

    public Player findOrCreateForReport(String canonicalName) {
        Objects.requireNonNull(canonicalName, "canonicalName");
        return playerRepository.findPlayerByExactName(canonicalName)
                .orElseGet(() -> Player.createNew(canonicalName));
    }
}
