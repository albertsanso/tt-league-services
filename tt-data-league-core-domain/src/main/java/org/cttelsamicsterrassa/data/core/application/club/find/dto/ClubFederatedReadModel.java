package org.cttelsamicsterrassa.data.core.application.club.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.Objects;
import java.util.UUID;

public record ClubFederatedReadModel(UUID id, String name, ImportSource source) {
    public ClubFederatedReadModel {
        Objects.requireNonNull(id, "id must not be null");
    }
}
