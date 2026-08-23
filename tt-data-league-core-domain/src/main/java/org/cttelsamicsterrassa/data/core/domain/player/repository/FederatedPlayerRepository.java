package org.cttelsamicsterrassa.data.core.domain.player.repository;

import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link FederatedPlayer}, the season-independent player identity.
 */
public interface FederatedPlayerRepository {
    Optional<FederatedPlayer> findFederatedPlayerById(UUID id);
    Optional<FederatedPlayer> findFederatedPlayerBySourceAndName(ImportSource source, String name);
    void saveFederatedPlayer(FederatedPlayer player);
    void deleteFederatedPlayerById(UUID id);

    List<FederatedPlayer> findAllFederatedPlayersByFragmentsInName(List<String> split);
}
