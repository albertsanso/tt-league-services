package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record FederatedClubDetailsReadModel(
        UUID id,
        String name,
        ImportSource source,
        List<FederatedClubTeamReadModel> teams,
        List<FederatedClubCompetitionReadModel> competitions,
        List<FederatedClubPlayerReadModel> players) {

    public FederatedClubDetailsReadModel(
            UUID id,
            String name,
            ImportSource source,
            List<FederatedClubTeamReadModel> teams,
            List<FederatedClubCompetitionReadModel> competitions) {
        this(id, name, source, teams, competitions, List.of());
    }

    public FederatedClubDetailsReadModel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(source, "source must not be null");
        teams = List.copyOf(Objects.requireNonNull(teams, "teams must not be null"));
        competitions = List.copyOf(Objects.requireNonNull(competitions, "competitions must not be null"));
        players = List.copyOf(Objects.requireNonNull(players, "players must not be null"));
    }
}
