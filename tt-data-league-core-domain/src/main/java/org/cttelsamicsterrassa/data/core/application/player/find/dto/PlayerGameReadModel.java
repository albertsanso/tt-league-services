package org.cttelsamicsterrassa.data.core.application.player.find.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlayerGameReadModel(
        UUID id,
        int gameNumber,
        String type,
        String result,
        Integer homeSetsWon,
        Integer awaySetsWon,
        List<PlayerOpponentReadModel> opponents,
        String unavailableReason) {
    public PlayerGameReadModel {
        Objects.requireNonNull(id, "id must not be null");
        opponents = List.copyOf(Objects.requireNonNull(opponents));
    }
}
