package org.cttelsamicsterrassa.data.core.application.player.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.UUID;

public record PlayerRegistrationReadModel(
        UUID id, String name, String license, Season season, ImportSource source, UUID federatedPlayerId) {
}
