package org.cttelsamicsterrassa.data.core.application.player.find.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlayerDetailsReadModel(
        UUID id, String name, List<PlayerFederatedReadModel> federatedPlayers,
        List<PlayerRegistrationReadModel> registrations, List<PlayerClubReadModel> clubs,
        List<PlayerCompetitionReadModel> competitions, List<PlayerMatchReadModel> matches) {
    public PlayerDetailsReadModel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        federatedPlayers = List.copyOf(Objects.requireNonNull(federatedPlayers));
        registrations = List.copyOf(Objects.requireNonNull(registrations));
        clubs = List.copyOf(Objects.requireNonNull(clubs));
        competitions = List.copyOf(Objects.requireNonNull(competitions));
        matches = List.copyOf(Objects.requireNonNull(matches));
    }
}
