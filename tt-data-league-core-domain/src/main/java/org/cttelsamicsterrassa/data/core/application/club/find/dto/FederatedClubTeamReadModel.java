package org.cttelsamicsterrassa.data.core.application.club.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.Objects;
import java.util.UUID;

public record FederatedClubTeamReadModel(UUID id, String name, ImportSource source, Season season) {
    public FederatedClubTeamReadModel {
        Objects.requireNonNull(id, "id must not be null");
    }
}
