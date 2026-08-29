package org.cttelsamicsterrassa.data.core.application.player.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.UUID;

public record PlayerFederatedReadModel(UUID id, String name, String license, ImportSource source) {
}
