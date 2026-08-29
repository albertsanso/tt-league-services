package org.cttelsamicsterrassa.data.core.application.player.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.UUID;

public record PlayerOpponentReadModel(
        UUID playerId,
        UUID federatedPlayerId,
        UUID playerSeasonId,
        String name,
        ImportSource source,
        Season season,
        boolean available) {
}
