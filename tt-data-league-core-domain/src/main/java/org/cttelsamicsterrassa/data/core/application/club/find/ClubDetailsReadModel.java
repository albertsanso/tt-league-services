package org.cttelsamicsterrassa.data.core.application.club.find;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClubDetailsReadModel(
        UUID id,
        String name,
        List<ClubFederatedReadModel> federatedClubs,
        List<FederatedClubTeamReadModel> teams,
        List<ClubCompetitionReadModel> competitions,
        List<FederatedClubPlayerReadModel> players) {
    public ClubDetailsReadModel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        federatedClubs = List.copyOf(Objects.requireNonNull(federatedClubs, "federatedClubs must not be null"));
        teams = List.copyOf(Objects.requireNonNull(teams, "teams must not be null"));
        competitions = List.copyOf(Objects.requireNonNull(competitions, "competitions must not be null"));
        players = List.copyOf(Objects.requireNonNull(players, "players must not be null"));
    }
}
