package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClubDetailsReadModel(
        UUID id,
        String name,
        ImportSource source,
        List<ClubTeamReadModel> teams,
        List<ClubCompetitionReadModel> competitions) {

    public ClubDetailsReadModel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(source, "source must not be null");
        teams = List.copyOf(Objects.requireNonNull(teams, "teams must not be null"));
        competitions = List.copyOf(Objects.requireNonNull(competitions, "competitions must not be null"));
    }
}
