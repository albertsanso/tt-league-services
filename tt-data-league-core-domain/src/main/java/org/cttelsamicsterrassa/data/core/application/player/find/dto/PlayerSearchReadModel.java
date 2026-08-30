package org.cttelsamicsterrassa.data.core.application.player.find.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlayerSearchReadModel(
        UUID id,
        String name,
        UUID canonicalPlayerId,
        List<PlayerFederatedReadModel> federatedPlayers,
        List<String> seasons) {
    public PlayerSearchReadModel(
            UUID id,
            String name,
            UUID canonicalPlayerId,
            List<PlayerFederatedReadModel> federatedPlayers) {
        this(id, name, canonicalPlayerId, federatedPlayers, List.of());
    }

    public PlayerSearchReadModel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        federatedPlayers = List.copyOf(Objects.requireNonNull(federatedPlayers, "federatedPlayers must not be null"));
        seasons = List.copyOf(Objects.requireNonNull(seasons, "seasons must not be null"));
    }
}
