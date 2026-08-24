package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClubSearchReadModel(
        UUID id,
        String name,
        List<ClubFederatedReadModel> federatedClubs,
        List<ClubCompetitionReadModel> competitions,
        int playerCount,
        List<Season> seasons) {
    public ClubSearchReadModel(
            UUID id,
            String name,
            List<ClubFederatedReadModel> federatedClubs) {
        this(id, name, federatedClubs, List.of(), 0, List.of());
    }

    public ClubSearchReadModel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        federatedClubs = List.copyOf(Objects.requireNonNull(federatedClubs, "federatedClubs must not be null"));
        competitions = List.copyOf(Objects.requireNonNull(competitions, "competitions must not be null"));
        if (playerCount < 0) {
            throw new IllegalArgumentException("playerCount must not be negative");
        }
        seasons = List.copyOf(Objects.requireNonNull(seasons, "seasons must not be null"));
    }
}
